package com.example.requests.application;

import com.example.requests.domain.PageQuery;
import com.example.requests.domain.Request;
import com.example.requests.domain.RequestAction;
import com.example.requests.domain.RequestFilter;
import com.example.requests.domain.RequestLifecycle;
import com.example.requests.domain.RequestState;
import com.example.requests.domain.StateChange;
import com.example.requests.domain.exception.ContentNotEditableException;
import com.example.requests.domain.exception.DomainValidationException;
import com.example.requests.domain.exception.InvalidStateTransitionException;
import com.example.requests.domain.exception.RequestNotFoundException;
import com.example.requests.domain.port.StateChangeLog;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestServiceTest {

	private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

	private final InMemoryRequestRepository repository = new InMemoryRequestRepository();
	private final List<StateChange> auditLog = new ArrayList<>();
	private final AtomicLong sequence = new AtomicLong();
	private final RequestService service = new RequestService(repository, new StateChangeLog() {
		@Override
		public void append(StateChange change) {
			auditLog.add(change);
		}

		@Override
		public List<StateChange> findByRequestId(long requestId) {
			return auditLog.stream().filter(c -> c.requestId() == requestId).toList();
		}
	}, sequence::incrementAndGet, RequestLifecycle.standard(), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void createPersistsRequestAndRecordsCreation() {
		Request created = service.create("Grant", "Body");

		assertThat(created.id()).isEqualTo(1L);
		assertThat(created.state()).isEqualTo(RequestState.CREATED);
		assertThat(created.createdAt()).isEqualTo(NOW);
		assertThat(repository.findById(1L)).contains(created);
		assertThat(auditLog).containsExactly(
				new StateChange(1L, RequestAction.CREATE, null, RequestState.CREATED, null, NOW));
	}

	@Test
	void invalidCreationPersistsNothing() {
		assertThatThrownBy(() -> service.create(" ", "Body")).isInstanceOf(DomainValidationException.class);

		assertThat(repository.findById(1L)).isEmpty();
		assertThat(auditLog).isEmpty();
	}

	@Test
	void getReturnsRequestOrFailsWithNotFound() {
		Request created = service.create("Grant", "Body");

		assertThat(service.get(created.id())).isEqualTo(created);
		assertThatThrownBy(() -> service.get(99)).isInstanceOf(RequestNotFoundException.class)
				.hasMessage("Request 99 not found");
	}

	@Test
	void editContentIsPersistedAndNotAudited() {
		long id = service.create("Grant", "Body").id();

		Request edited = service.editContent(id, "New body");

		assertThat(edited.content()).isEqualTo("New body");
		assertThat(service.get(id).content()).isEqualTo("New body");
		assertThat(auditLog).hasSize(1);
	}

	@Test
	void editContentAfterAcceptanceIsRefused() {
		long id = service.create("Grant", "Body").id();
		service.transition(id, RequestAction.VERIFY, null);
		service.transition(id, RequestAction.ACCEPT, null);

		assertThatThrownBy(() -> service.editContent(id, "New body")).isInstanceOf(ContentNotEditableException.class);
		assertThat(service.get(id).content()).isEqualTo("Body");
	}

	@Test
	void fullLifecycleIsAuditedInOrderAndPublicationNumbersAreUnique() {
		long first = publishNew("First");
		long second = publishNew("Second");

		assertThat(service.get(first).publicationNumber()).isEqualTo(1L);
		assertThat(service.get(second).publicationNumber()).isEqualTo(2L);
		assertThat(service.history(first)).extracting(StateChange::action)
				.containsExactly(RequestAction.CREATE, RequestAction.VERIFY, RequestAction.ACCEPT, RequestAction.PUBLISH);
		assertThat(service.history(first)).extracting(StateChange::toState)
				.containsExactly(RequestState.CREATED, RequestState.VERIFIED, RequestState.ACCEPTED,
						RequestState.PUBLISHED);
	}

	@Test
	void rejectionStoresReasonOnRequestAndInHistory() {
		long id = service.create("Grant", "Body").id();
		service.transition(id, RequestAction.VERIFY, null);

		Request rejected = service.transition(id, RequestAction.REJECT, "Incomplete");

		assertThat(rejected.state()).isEqualTo(RequestState.REJECTED);
		assertThat(rejected.reason()).isEqualTo("Incomplete");
		assertThat(service.history(id)).last().extracting(StateChange::reason).isEqualTo("Incomplete");
	}

	@Test
	void failedTransitionChangesNothingAndIsNotAudited() {
		long id = service.create("Grant", "Body").id();

		assertThatThrownBy(() -> service.transition(id, RequestAction.PUBLISH, null))
				.isInstanceOf(InvalidStateTransitionException.class);
		assertThatThrownBy(() -> service.transition(id, RequestAction.DELETE, " "))
				.isInstanceOf(DomainValidationException.class);

		assertThat(service.get(id).state()).isEqualTo(RequestState.CREATED);
		assertThat(auditLog).hasSize(1);
		assertThat(sequence.get()).isZero();
	}

	@Test
	void operationsOnUnknownRequestFailWithNotFound() {
		assertThatThrownBy(() -> service.transition(42, RequestAction.VERIFY, null))
				.isInstanceOf(RequestNotFoundException.class);
		assertThatThrownBy(() -> service.editContent(42, "Body")).isInstanceOf(RequestNotFoundException.class);
		assertThatThrownBy(() -> service.history(42)).isInstanceOf(RequestNotFoundException.class);
	}

	@Test
	void searchDelegatesFilterAndPaging() {
		service.create("Alpha", "Body");
		service.create("Beta", "Body");
		service.create("alphabet", "Body");

		assertThat(service.search(new RequestFilter("ALPHA", null), new PageQuery(0, 1)))
				.satisfies(page -> {
					assertThat(page.items()).extracting(Request::name).containsExactly("Alpha");
					assertThat(page.totalItems()).isEqualTo(2);
					assertThat(page.totalPages()).isEqualTo(2);
				});
	}

	private long publishNew(String name) {
		long id = service.create(name, "Body").id();
		service.transition(id, RequestAction.VERIFY, null);
		service.transition(id, RequestAction.ACCEPT, null);
		service.transition(id, RequestAction.PUBLISH, null);
		return id;
	}
}
