package com.example.requests.service.impl;

import com.example.requests.exception.ContentNotEditableException;
import com.example.requests.exception.DomainValidationException;
import com.example.requests.exception.InvalidStateTransitionException;
import com.example.requests.exception.RequestNotFoundException;
import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.PublicationNumberGenerator;
import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestFilter;
import com.example.requests.model.RequestLifecycle;
import com.example.requests.model.RequestState;
import com.example.requests.model.StateChange;
import com.example.requests.repository.RequestRepository;
import com.example.requests.repository.StateChangeLog;
import com.example.requests.service.RequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RequestServiceImplTest {

	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID MISSING = UUID.fromString("00000000-0000-0000-0000-00000000dead");

	@Mock
	private RequestRepository requests;

	@Mock
	private StateChangeLog stateChangeLog;

	@Mock
	private PublicationNumberGenerator publicationNumbers;

	@Captor
	private ArgumentCaptor<Request> savedRequest;

	private RequestService service;

	@BeforeEach
	void setUp() {
		service = new RequestServiceImpl(requests, stateChangeLog, publicationNumbers, RequestLifecycle.standard(),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Nested
	class Create {

		@Test
		void persistsNewRequestWithItsOwnIdAndRecordsCreation() {
			given(requests.save(any())).willAnswer(invocation -> invocation.getArgument(0));

			Request created = service.create("Grant", "Body");

			verify(requests).save(savedRequest.capture());
			assertThat(savedRequest.getValue().id()).isNotNull().isEqualTo(created.id());
			assertThat(savedRequest.getValue().state()).isEqualTo(RequestState.CREATED);
			assertThat(savedRequest.getValue().createdAt()).isEqualTo(NOW);
			verify(stateChangeLog).append(
					new StateChange(created.id(), RequestAction.CREATE, null, RequestState.CREATED, null, NOW));
		}

		@Test
		void requestsWithTheSameNameAreAllowed() {
			given(requests.save(any())).willAnswer(invocation -> invocation.getArgument(0));

			Request first = service.create("Grant", "Body");
			Request second = service.create("Grant", "Body");

			assertThat(first.id()).isNotEqualTo(second.id());
		}

		@Test
		void invalidInputTouchesNothing() {
			assertThatThrownBy(() -> service.create(" ", "Body")).isInstanceOf(DomainValidationException.class);

			verifyNoInteractions(requests, stateChangeLog);
		}
	}

	@Nested
	class Get {

		@Test
		void returnsStoredRequest() {
			Request stored = givenStored(RequestState.CREATED);

			assertThat(service.get(ID)).isSameAs(stored);
		}

		@Test
		void unknownIdIsNotFound() {
			given(requests.findById(MISSING)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.get(MISSING))
					.isInstanceOf(RequestNotFoundException.class)
					.hasMessage("Request " + MISSING + " not found");
		}
	}

	@Nested
	class EditContent {

		@Test
		void savesNewContentWithoutAuditEntry() {
			Request stored = givenStored(RequestState.VERIFIED);
			given(requests.save(stored)).willReturn(stored);

			Request edited = service.editContent(ID, "New body");

			assertThat(edited.content()).isEqualTo("New body");
			assertThat(edited.updatedAt()).isEqualTo(NOW);
			verify(requests).save(stored);
			verifyNoInteractions(stateChangeLog);
		}

		@Test
		void refusedOutsideEditableStates() {
			givenStored(RequestState.ACCEPTED);

			assertThatThrownBy(() -> service.editContent(ID, "New body"))
					.isInstanceOf(ContentNotEditableException.class);

			verify(requests, never()).save(any());
			verifyNoInteractions(stateChangeLog);
		}

		@Test
		void unknownIdIsNotFound() {
			given(requests.findById(MISSING)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.editContent(MISSING, "Body"))
					.isInstanceOf(RequestNotFoundException.class);
			verify(requests, never()).save(any());
		}
	}

	@Nested
	class Transition {

		@Test
		void savesRequestBeforeRecordingTheChange() {
			Request stored = givenStored(RequestState.CREATED);
			given(requests.save(stored)).willReturn(stored);

			Request verified = service.transition(ID, RequestAction.VERIFY, null);

			assertThat(verified.state()).isEqualTo(RequestState.VERIFIED);
			InOrder order = inOrder(requests, stateChangeLog);
			order.verify(requests).save(stored);
			order.verify(stateChangeLog).append(new StateChange(ID, RequestAction.VERIFY, RequestState.CREATED,
					RequestState.VERIFIED, null, NOW));
			verifyNoInteractions(publicationNumbers);
		}

		@Test
		void publishingAssignsNumberFromGenerator() {
			Request stored = givenStored(RequestState.ACCEPTED);
			given(requests.save(stored)).willReturn(stored);
			given(publicationNumbers.next()).willReturn(42L);

			Request published = service.transition(ID, RequestAction.PUBLISH, null);

			assertThat(published.state()).isEqualTo(RequestState.PUBLISHED);
			assertThat(published.publicationNumber()).isEqualTo(42L);
			verify(publicationNumbers).next();
		}

		@Test
		void rejectionKeepsReasonOnRequestAndAuditEntry() {
			Request stored = givenStored(RequestState.VERIFIED);
			given(requests.save(stored)).willReturn(stored);

			Request rejected = service.transition(ID, RequestAction.REJECT, "Incomplete");

			assertThat(rejected.reason()).isEqualTo("Incomplete");
			verify(stateChangeLog).append(new StateChange(ID, RequestAction.REJECT, RequestState.VERIFIED,
					RequestState.REJECTED, "Incomplete", NOW));
		}

		@Test
		void forbiddenTransitionChangesNothing() {
			Request stored = givenStored(RequestState.CREATED);

			assertThatThrownBy(() -> service.transition(ID, RequestAction.PUBLISH, null))
					.isInstanceOf(InvalidStateTransitionException.class);

			assertThat(stored.state()).isEqualTo(RequestState.CREATED);
			verify(requests, never()).save(any());
			verifyNoInteractions(stateChangeLog, publicationNumbers);
		}

		@Test
		void missingReasonChangesNothing() {
			Request stored = givenStored(RequestState.CREATED);

			assertThatThrownBy(() -> service.transition(ID, RequestAction.DELETE, " "))
					.isInstanceOf(DomainValidationException.class);

			assertThat(stored.state()).isEqualTo(RequestState.CREATED);
			verify(requests, never()).save(any());
			verifyNoInteractions(stateChangeLog);
		}

		@Test
		void concurrentModificationPropagatesAndNothingIsAudited() {
			givenStored(RequestState.ACCEPTED);
			given(publicationNumbers.next()).willReturn(42L);
			given(requests.save(any())).willThrow(new OptimisticLockingFailureException("stale"));

			assertThatThrownBy(() -> service.transition(ID, RequestAction.PUBLISH, null))
					.isInstanceOf(OptimisticLockingFailureException.class);

			verifyNoInteractions(stateChangeLog);
		}

		@Test
		void unknownIdIsNotFound() {
			given(requests.findById(MISSING)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.transition(MISSING, RequestAction.VERIFY, null))
					.isInstanceOf(RequestNotFoundException.class);
			verifyNoInteractions(stateChangeLog, publicationNumbers);
		}
	}

	@Nested
	class Queries {

		@Test
		void browseDelegatesToRepository() {
			RequestFilter filter = new RequestFilter("grant", RequestState.CREATED);
			PageQuery pageQuery = new PageQuery(1, 5);
			PageResult<Request> page = new PageResult<>(List.of(stored(RequestState.CREATED)), 1, 5, 6);
			given(requests.search(filter, pageQuery)).willReturn(page);

			assertThat(service.browse(filter, pageQuery)).isSameAs(page);
		}

		@Test
		void auditLogOfExistingRequestIsReturned() {
			givenStored(RequestState.VERIFIED);
			List<StateChange> history = List.of(
					new StateChange(ID, RequestAction.CREATE, null, RequestState.CREATED, null, CREATED_AT),
					new StateChange(ID, RequestAction.VERIFY, RequestState.CREATED, RequestState.VERIFIED, null, NOW));
			given(stateChangeLog.findByRequestId(ID)).willReturn(history);

			assertThat(service.auditLog(ID)).isEqualTo(history);
		}

		@Test
		void auditLogOfUnknownRequestIsNotFound() {
			given(requests.findById(MISSING)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.auditLog(MISSING)).isInstanceOf(RequestNotFoundException.class);
			verifyNoInteractions(stateChangeLog);
		}
	}

	private Request givenStored(RequestState state) {
		Request request = stored(state);
		given(requests.findById(ID)).willReturn(Optional.of(request));
		return request;
	}

	private static Request stored(RequestState state) {
		Long publicationNumber = state == RequestState.PUBLISHED ? 7L : null;
		return Request.restore(ID, "Grant", "Body", state, publicationNumber, null, CREATED_AT, CREATED_AT);
	}
}
