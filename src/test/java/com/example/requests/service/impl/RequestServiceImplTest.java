package com.example.requests.service.impl;

import com.example.requests.exception.ContentNotEditableException;
import com.example.requests.exception.DomainValidationException;
import com.example.requests.exception.DuplicateRequestNameException;
import com.example.requests.exception.InvalidStateTransitionException;
import com.example.requests.exception.RequestNotFoundException;
import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestFilter;
import com.example.requests.model.RequestLifecycle;
import com.example.requests.model.RequestState;
import com.example.requests.model.StateChange;
import com.example.requests.repository.PublicationNumberGenerator;
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
		void persistsNewRequestAndRecordsCreation() {
			given(requests.save(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 1L));

			Request created = service.create("Grant", "Body");

			verify(requests).save(savedRequest.capture());
			assertThat(savedRequest.getValue().id()).isNull();
			assertThat(savedRequest.getValue().state()).isEqualTo(RequestState.CREATED);
			assertThat(savedRequest.getValue().createdAt()).isEqualTo(NOW);
			assertThat(created.id()).isEqualTo(1L);
			verify(stateChangeLog).append(
					new StateChange(1L, RequestAction.CREATE, null, RequestState.CREATED, null, NOW));
		}

		@Test
		void duplicateNameIsRejectedAndNotAudited() {
			given(requests.save(any())).willThrow(new DuplicateRequestNameException("Grant"));

			assertThatThrownBy(() -> service.create("Grant", "Body"))
					.isInstanceOf(DuplicateRequestNameException.class);

			verifyNoInteractions(stateChangeLog);
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

			assertThat(service.get("Grant")).isSameAs(stored);
		}

		@Test
		void unknownIdIsNotFound() {
			given(requests.findByName("missing")).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.get("missing"))
					.isInstanceOf(RequestNotFoundException.class)
					.hasMessage("Request 'missing' not found");
		}
	}

	@Nested
	class EditContent {

		@Test
		void savesNewContentWithoutAuditEntry() {
			Request stored = givenStored(RequestState.VERIFIED);
			given(requests.save(stored)).willReturn(stored);

			Request edited = service.editContent("Grant", "New body");

			assertThat(edited.content()).isEqualTo("New body");
			assertThat(edited.updatedAt()).isEqualTo(NOW);
			verify(requests).save(stored);
			verifyNoInteractions(stateChangeLog);
		}

		@Test
		void refusedOutsideEditableStates() {
			givenStored(RequestState.ACCEPTED);

			assertThatThrownBy(() -> service.editContent("Grant", "New body"))
					.isInstanceOf(ContentNotEditableException.class);

			verify(requests, never()).save(any());
			verifyNoInteractions(stateChangeLog);
		}

		@Test
		void unknownIdIsNotFound() {
			given(requests.findByName("missing")).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.editContent("missing", "Body")).isInstanceOf(RequestNotFoundException.class);
			verify(requests, never()).save(any());
		}
	}

	@Nested
	class Transition {

		@Test
		void savesRequestBeforeRecordingTheChange() {
			Request stored = givenStored(RequestState.CREATED);
			given(requests.save(stored)).willReturn(stored);

			Request verified = service.transition("Grant", RequestAction.VERIFY, null);

			assertThat(verified.state()).isEqualTo(RequestState.VERIFIED);
			InOrder order = inOrder(requests, stateChangeLog);
			order.verify(requests).save(stored);
			order.verify(stateChangeLog).append(new StateChange(1L, RequestAction.VERIFY, RequestState.CREATED,
					RequestState.VERIFIED, null, NOW));
			verifyNoInteractions(publicationNumbers);
		}

		@Test
		void publishingAssignsNumberFromGenerator() {
			Request stored = givenStored(RequestState.ACCEPTED);
			given(requests.save(stored)).willReturn(stored);
			given(publicationNumbers.next()).willReturn(42L);

			Request published = service.transition("Grant", RequestAction.PUBLISH, null);

			assertThat(published.state()).isEqualTo(RequestState.PUBLISHED);
			assertThat(published.publicationNumber()).isEqualTo(42L);
			verify(publicationNumbers).next();
		}

		@Test
		void rejectionKeepsReasonOnRequestAndAuditEntry() {
			Request stored = givenStored(RequestState.VERIFIED);
			given(requests.save(stored)).willReturn(stored);

			Request rejected = service.transition("Grant", RequestAction.REJECT, "Incomplete");

			assertThat(rejected.reason()).isEqualTo("Incomplete");
			verify(stateChangeLog).append(new StateChange(1L, RequestAction.REJECT, RequestState.VERIFIED,
					RequestState.REJECTED, "Incomplete", NOW));
		}

		@Test
		void forbiddenTransitionChangesNothing() {
			Request stored = givenStored(RequestState.CREATED);

			assertThatThrownBy(() -> service.transition("Grant", RequestAction.PUBLISH, null))
					.isInstanceOf(InvalidStateTransitionException.class);

			assertThat(stored.state()).isEqualTo(RequestState.CREATED);
			verify(requests, never()).save(any());
			verifyNoInteractions(stateChangeLog, publicationNumbers);
		}

		@Test
		void missingReasonChangesNothing() {
			Request stored = givenStored(RequestState.CREATED);

			assertThatThrownBy(() -> service.transition("Grant", RequestAction.DELETE, " "))
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

			assertThatThrownBy(() -> service.transition("Grant", RequestAction.PUBLISH, null))
					.isInstanceOf(OptimisticLockingFailureException.class);

			verifyNoInteractions(stateChangeLog);
		}

		@Test
		void unknownIdIsNotFound() {
			given(requests.findByName("missing")).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.transition("missing", RequestAction.VERIFY, null))
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
			PageResult<Request> page = new PageResult<>(List.of(stored(1L, RequestState.CREATED)), 1, 5, 6);
			given(requests.search(filter, pageQuery)).willReturn(page);

			assertThat(service.browse(filter, pageQuery)).isSameAs(page);
		}

		@Test
		void historyReturnsAuditLogOfExistingRequest() {
			givenStored(RequestState.VERIFIED);
			List<StateChange> history = List.of(
					new StateChange(1L, RequestAction.CREATE, null, RequestState.CREATED, null, CREATED_AT),
					new StateChange(1L, RequestAction.VERIFY, RequestState.CREATED, RequestState.VERIFIED, null, NOW));
			given(stateChangeLog.findByRequestId(1L)).willReturn(history);

			assertThat(service.history("Grant")).isEqualTo(history);
		}

		@Test
		void historyOfUnknownRequestIsNotFound() {
			given(requests.findByName("missing")).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.history("missing")).isInstanceOf(RequestNotFoundException.class);
			verifyNoInteractions(stateChangeLog);
		}
	}

	private Request givenStored(RequestState state) {
		Request request = stored(1L, state);
		given(requests.findByName("Grant")).willReturn(Optional.of(request));
		return request;
	}

	private static Request stored(long id, RequestState state) {
		Long publicationNumber = state == RequestState.PUBLISHED ? 7L : null;
		return Request.restore(id, "Grant", "Body", state, publicationNumber, null, CREATED_AT, CREATED_AT);
	}

	private static Request withId(Request request, long id) {
		return Request.restore(id, request.name(), request.content(), request.state(), request.publicationNumber(),
				request.reason(), request.createdAt(), request.updatedAt());
	}
}
