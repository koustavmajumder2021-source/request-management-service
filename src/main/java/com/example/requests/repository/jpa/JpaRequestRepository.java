package com.example.requests.repository.jpa;

import com.example.requests.exception.DuplicateRequestNameException;
import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.Request;
import com.example.requests.model.RequestFilter;
import com.example.requests.repository.RequestRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
class JpaRequestRepository implements RequestRepository {

	private final RequestJpaRepository jpa;

	JpaRequestRepository(RequestJpaRepository jpa) {
		this.jpa = jpa;
	}

	/**
	 * Copies the domain state onto the managed entity instead of merging a detached copy, so the version
	 * read when the request was loaded is the one checked on flush. A concurrent change therefore fails with
	 * {@link org.springframework.dao.OptimisticLockingFailureException} rather than being overwritten.
	 */
	@Override
	public Request save(Request request) {
		RequestEntity entity = request.id() == null
				? new RequestEntity()
				: jpa.findById(request.id()).orElseThrow(() -> new IllegalStateException(
						"Request " + request.id() + " disappeared during the transaction"));
		entity.setName(request.name());
		entity.setContent(request.content());
		entity.setState(request.state());
		entity.setReason(request.reason());
		entity.setPublicationNumber(request.publicationNumber());
		entity.setCreatedAt(request.createdAt());
		entity.setUpdatedAt(request.updatedAt());
		try {
			return toDomain(jpa.saveAndFlush(entity));
		}
		catch (DataIntegrityViolationException ex) {
			if (violates(ex, RequestEntity.UNIQUE_ACTIVE_NAME)) {
				throw new DuplicateRequestNameException(request.name());
			}
			throw ex;
		}
	}

	/**
	 * The newest request with this name: the current one if the name is in use, otherwise the most
	 * recently deleted one. A name can only be reused after deletion, so the newest is always current.
	 */
	@Override
	public Optional<Request> findByName(String name) {
		return jpa.findFirstByNameOrderByIdDesc(name).map(JpaRequestRepository::toDomain);
	}

	/**
	 * Combines the non-null criteria with AND. The name criterion is a case-insensitive {@code LIKE}
	 * with any wildcard characters in the input escaped.
	 */
	@Override
	public PageResult<Request> search(RequestFilter filter, PageQuery pageQuery) {
		Page<RequestEntity> page = jpa.findAll(toSpecification(filter),
				PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by("id")));
		return new PageResult<>(page.getContent().stream().map(JpaRequestRepository::toDomain).toList(),
				pageQuery.page(), pageQuery.size(), page.getTotalElements());
	}

	private static Specification<RequestEntity> toSpecification(RequestFilter filter) {
		List<Specification<RequestEntity>> criteria = new ArrayList<>();
		if (filter.name() != null) {
			String pattern = "%" + escapeLike(filter.name().toLowerCase()) + "%";
			criteria.add((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
		}
		if (filter.state() != null) {
			criteria.add((root, query, cb) -> cb.equal(root.get("state"), filter.state()));
		}
		return Specification.allOf(criteria);
	}

	/**
	 * Relying on the database constraint (instead of checking first) keeps uniqueness correct when two
	 * requests with the same name are created concurrently.
	 */
	private static boolean violates(DataIntegrityViolationException ex, String constraint) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null
					&& violation.getConstraintName().toLowerCase(Locale.ROOT).contains(constraint)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Escapes {@code LIKE} wildcards so that user input is matched literally.
	 */
	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static Request toDomain(RequestEntity e) {
		return Request.restore(e.getId(), e.getName(), e.getContent(), e.getState(), e.getPublicationNumber(),
				e.getReason(), e.getCreatedAt(), e.getUpdatedAt());
	}
}
