package com.example.requests.repository.jpa;

import com.example.requests.exception.DuplicateRequestNameException;
import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestFilter;
import com.example.requests.model.RequestLifecycle;
import com.example.requests.model.RequestState;
import com.example.requests.model.StateChange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaRequestRepository.class, JpaStateChangeLog.class, SequencePublicationNumberGenerator.class})
class PersistenceAdaptersTest {

	private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

	@Autowired
	private JpaRequestRepository repository;

	@Autowired
	private JpaStateChangeLog stateChangeLog;

	@Autowired
	private SequencePublicationNumberGenerator generator;

	private final RequestLifecycle lifecycle = RequestLifecycle.standard();

	private Request saveNew(String name) {
		return repository.save(Request.create(name, "Body of " + name, lifecycle, NOW));
	}

	@Test
	void savesAndReloadsAllFields() {
		Request saved = saveNew("Grant");

		assertThat(saved.id()).isNotNull();
		assertThat(repository.findByName("Grant")).hasValueSatisfying(loaded -> {
			assertThat(loaded.id()).isEqualTo(saved.id());
			assertThat(loaded.name()).isEqualTo("Grant");
			assertThat(loaded.content()).isEqualTo("Body of Grant");
			assertThat(loaded.state()).isEqualTo(RequestState.CREATED);
			assertThat(loaded.createdAt()).isEqualTo(NOW);
			assertThat(loaded.publicationNumber()).isNull();
		});
	}

	@Test
	void updatesExistingRequest() {
		Request request = saveNew("Grant");
		request.apply(RequestAction.DELETE, "duplicate", lifecycle, generator, NOW.plus(1, ChronoUnit.HOURS));

		repository.save(request);

		assertThat(repository.findByName("Grant")).hasValueSatisfying(loaded -> {
			assertThat(loaded.state()).isEqualTo(RequestState.DELETED);
			assertThat(loaded.reason()).isEqualTo("duplicate");
			assertThat(loaded.updatedAt()).isEqualTo(NOW.plus(1, ChronoUnit.HOURS));
		});
	}

	@Test
	void savingARequestThatNoLongerExistsFails() {
		Request ghost = Request.restore(987_654L, "n", "c", RequestState.CREATED, null, null, NOW, NOW);

		assertThatThrownBy(() -> repository.save(ghost)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void findByNameOfUnknownRequestIsEmpty() {
		assertThat(repository.findByName("does-not-exist")).isEmpty();
	}

	@Test
	void nameMustBeUniqueAmongRequestsThatAreNotDeleted() {
		saveNew("Grant");

		assertThatThrownBy(() -> saveNew("Grant"))
				.isInstanceOf(DuplicateRequestNameException.class)
				.hasMessage("A request named 'Grant' already exists");
	}

	@Test
	void nameOfDeletedRequestCanBeReusedAndLookupReturnsTheNewRequest() {
		Request deleted = saveNew("Grant");
		deleted.apply(RequestAction.DELETE, "duplicate", lifecycle, generator, NOW);
		repository.save(deleted);

		Request reused = saveNew("Grant");

		assertThat(reused.id()).isGreaterThan(deleted.id());
		assertThat(repository.findByName("Grant")).hasValueSatisfying(found -> {
			assertThat(found.id()).isEqualTo(reused.id());
			assertThat(found.state()).isEqualTo(RequestState.CREATED);
		});
	}

	@Test
	void reusedNameIsReservedAgainWhileTheNewRequestIsActive() {
		Request deleted = saveNew("Grant");
		deleted.apply(RequestAction.DELETE, "duplicate", lifecycle, generator, NOW);
		repository.save(deleted);
		saveNew("Grant");

		assertThatThrownBy(() -> saveNew("Grant")).isInstanceOf(DuplicateRequestNameException.class);
	}

	@Test
	void deletedRequestIsFoundByNameUntilTheNameIsReused() {
		Request deleted = saveNew("Grant");
		deleted.apply(RequestAction.DELETE, "duplicate", lifecycle, generator, NOW);
		repository.save(deleted);

		assertThat(repository.findByName("Grant")).hasValueSatisfying(
				found -> assertThat(found.state()).isEqualTo(RequestState.DELETED));
	}

	@Test
	void searchFiltersByNameCaseInsensitivelyAndByState() {
		Request alpha = saveNew("Alpha project");
		saveNew("Beta project");
		Request alphabet = saveNew("ALPHABET");
		alphabet.apply(RequestAction.VERIFY, null, lifecycle, generator, NOW);
		repository.save(alphabet);

		assertThat(names(repository.search(new RequestFilter("alpha", null), new PageQuery(0, 10))))
				.containsExactly("Alpha project", "ALPHABET");
		assertThat(names(repository.search(new RequestFilter(null, RequestState.VERIFIED), new PageQuery(0, 10))))
				.containsExactly("ALPHABET");
		assertThat(names(repository.search(new RequestFilter("alpha", RequestState.CREATED), new PageQuery(0, 10))))
				.containsExactly(alpha.name());
		assertThat(repository.search(new RequestFilter("gamma", null), new PageQuery(0, 10)).items()).isEmpty();
	}

	@Test
	void likeWildcardsInTheFilterAreMatchedLiterally() {
		saveNew("100% done");
		saveNew("1000 done");
		saveNew("snake_case");
		saveNew("snakeXcase");

		assertThat(names(repository.search(new RequestFilter("0%", null), new PageQuery(0, 10))))
				.containsExactly("100% done");
		assertThat(names(repository.search(new RequestFilter("e_c", null), new PageQuery(0, 10))))
				.containsExactly("snake_case");
	}

	@Test
	void searchPagesInCreationOrder() {
		for (int i = 1; i <= 12; i++) {
			saveNew("Paged %02d".formatted(i));
		}
		RequestFilter filter = new RequestFilter("Paged", null);

		PageResult<Request> first = repository.search(filter, new PageQuery(0, 10));
		PageResult<Request> second = repository.search(filter, new PageQuery(1, 10));
		PageResult<Request> beyond = repository.search(filter, new PageQuery(5, 10));

		assertThat(names(first)).hasSize(10).first().isEqualTo("Paged 01");
		assertThat(names(second)).containsExactly("Paged 11", "Paged 12");
		assertThat(first.totalItems()).isEqualTo(12);
		assertThat(first.totalPages()).isEqualTo(2);
		assertThat(beyond.items()).isEmpty();
		assertThat(beyond.totalItems()).isEqualTo(12);
	}

	@Test
	void sequenceProducesIncreasingUniqueNumbers() {
		long first = generator.next();
		long second = generator.next();

		assertThat(second).isGreaterThan(first);
	}

	@Test
	void databaseRejectsDuplicatePublicationNumbers() {
		published("a", 5_000L);

		assertThatThrownBy(() -> published("b", 5_000L)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsPublishedRequestWithoutNumber() {
		Request request = saveNew("Grant");
		Request corrupted = Request.restore(request.id(), "Grant", "c", RequestState.PUBLISHED, null, null, NOW, NOW);

		assertThatThrownBy(() -> repository.save(corrupted)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void auditLogIsReturnedPerRequestInInsertionOrder() {
		Request first = saveNew("First");
		Request second = saveNew("Second");
		stateChangeLog.append(StateChange.creationOf(first));
		stateChangeLog.append(StateChange.creationOf(second));
		stateChangeLog.append(new StateChange(first.id(), RequestAction.DELETE, RequestState.CREATED,
				RequestState.DELETED, "duplicate", NOW));

		assertThat(stateChangeLog.findByRequestId(first.id())).containsExactly(
				new StateChange(first.id(), RequestAction.CREATE, null, RequestState.CREATED, null, NOW),
				new StateChange(first.id(), RequestAction.DELETE, RequestState.CREATED, RequestState.DELETED,
						"duplicate", NOW));
		assertThat(stateChangeLog.findByRequestId(987_654L)).isEmpty();
	}

	private Request published(String name, long number) {
		Request request = saveNew(name);
		request.apply(RequestAction.VERIFY, null, lifecycle, generator, NOW);
		request.apply(RequestAction.ACCEPT, null, lifecycle, generator, NOW);
		request.apply(RequestAction.PUBLISH, null, lifecycle, () -> number, NOW);
		return repository.save(request);
	}

	private static List<String> names(PageResult<Request> page) {
		return page.items().stream().map(Request::name).toList();
	}
}
