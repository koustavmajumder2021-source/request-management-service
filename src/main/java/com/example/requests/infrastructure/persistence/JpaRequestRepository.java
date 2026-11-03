package com.example.requests.infrastructure.persistence;

import com.example.requests.domain.PageQuery;
import com.example.requests.domain.PageResult;
import com.example.requests.domain.Request;
import com.example.requests.domain.RequestFilter;
import com.example.requests.domain.port.RequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
class JpaRequestRepository implements RequestRepository {

	private final RequestJpaRepository jpa;

	JpaRequestRepository(RequestJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Request save(Request request) {
		// Update the managed instance so the version read at load time is the one checked on flush.
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
		return toDomain(jpa.saveAndFlush(entity));
	}

	@Override
	public Optional<Request> findById(long id) {
		return jpa.findById(id).map(JpaRequestRepository::toDomain);
	}

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

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static Request toDomain(RequestEntity e) {
		return Request.restore(e.getId(), e.getName(), e.getContent(), e.getState(), e.getPublicationNumber(),
				e.getReason(), e.getCreatedAt(), e.getUpdatedAt());
	}
}
