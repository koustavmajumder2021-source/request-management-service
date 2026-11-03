package com.example.requests.domain;

import com.example.requests.domain.exception.ContentNotEditableException;
import com.example.requests.domain.exception.DomainValidationException;
import com.example.requests.domain.exception.InvalidStateTransitionException;
import com.example.requests.domain.port.PublicationNumberGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestTest {

	private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
	private static final Instant LATER = Instant.parse("2026-01-02T10:00:00Z");

	private final RequestLifecycle lifecycle = RequestLifecycle.standard();
	private final AtomicLong numbers = new AtomicLong(100);
	private final PublicationNumberGenerator generator = numbers::incrementAndGet;

	private static final PublicationNumberGenerator NO_NUMBER_EXPECTED = () -> {
		throw new AssertionError("publication number must not be drawn");
	};

	private static Request inState(RequestState state) {
		Long number = state == RequestState.PUBLISHED ? 7L : null;
		return Request.restore(1L, "name", "content", state, number, null, CREATED_AT, CREATED_AT);
	}

	@Nested
	class Creation {

		@Test
		void createsRequestInInitialState() {
			Request request = Request.create("Grant", "Body", lifecycle, CREATED_AT);

			assertThat(request.id()).isNull();
			assertThat(request.name()).isEqualTo("Grant");
			assertThat(request.content()).isEqualTo("Body");
			assertThat(request.state()).isEqualTo(RequestState.CREATED);
			assertThat(request.publicationNumber()).isNull();
			assertThat(request.reason()).isNull();
			assertThat(request.createdAt()).isEqualTo(CREATED_AT);
			assertThat(request.updatedAt()).isEqualTo(CREATED_AT);
		}

		@Test
		void trimsNameButPreservesContentWhitespace() {
			Request request = Request.create("  Grant  ", "  indented body\n", lifecycle, CREATED_AT);

			assertThat(request.name()).isEqualTo("Grant");
			assertThat(request.content()).isEqualTo("  indented body\n");
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {" ", "\t\n"})
		void requiresName(String name) {
			assertThatThrownBy(() -> Request.create(name, "Body", lifecycle, CREATED_AT))
					.isInstanceOfSatisfying(DomainValidationException.class,
							ex -> assertThat(ex.field()).isEqualTo("name"));
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {" ", "\t\n"})
		void requiresContent(String content) {
			assertThatThrownBy(() -> Request.create("Grant", content, lifecycle, CREATED_AT))
					.isInstanceOfSatisfying(DomainValidationException.class,
							ex -> assertThat(ex.field()).isEqualTo("content"));
		}

		@Test
		void acceptsValuesAtMaximumLength() {
			Request request = Request.create("n".repeat(Request.MAX_NAME_LENGTH),
					"c".repeat(Request.MAX_CONTENT_LENGTH), lifecycle, CREATED_AT);

			assertThat(request.name()).hasSize(Request.MAX_NAME_LENGTH);
			assertThat(request.content()).hasSize(Request.MAX_CONTENT_LENGTH);
		}

		@Test
		void rejectsValuesAboveMaximumLength() {
			assertThatThrownBy(() -> Request.create("n".repeat(Request.MAX_NAME_LENGTH + 1), "Body", lifecycle,
					CREATED_AT))
					.isInstanceOf(DomainValidationException.class)
					.hasMessage("name must be at most 255 characters");
			assertThatThrownBy(() -> Request.create("Grant", "c".repeat(Request.MAX_CONTENT_LENGTH + 1), lifecycle,
					CREATED_AT))
					.isInstanceOf(DomainValidationException.class)
					.hasMessage("content must be at most 10000 characters");
		}

		@Test
		void creationAuditEntryHasNoPreviousState() {
			Request request = inState(RequestState.CREATED);

			assertThat(StateChange.creationOf(request)).isEqualTo(
					new StateChange(1L, RequestAction.CREATE, null, RequestState.CREATED, null, CREATED_AT));
		}
	}

	@Nested
	class ContentEditing {

		@ParameterizedTest
		@EnumSource(names = {"CREATED", "VERIFIED"})
		void editsContentInEditableStates(RequestState state) {
			Request request = inState(state);

			request.editContent("new body", lifecycle, LATER);

			assertThat(request.content()).isEqualTo("new body");
			assertThat(request.updatedAt()).isEqualTo(LATER);
			assertThat(request.state()).isEqualTo(state);
		}

		@ParameterizedTest
		@EnumSource(names = {"ACCEPTED", "PUBLISHED", "REJECTED", "DELETED"})
		void refusesToEditInOtherStates(RequestState state) {
			Request request = inState(state);

			assertThatThrownBy(() -> request.editContent("new body", lifecycle, LATER))
					.isInstanceOfSatisfying(ContentNotEditableException.class,
							ex -> assertThat(ex.currentState()).isEqualTo(state))
					.hasMessage("Content cannot be edited in state " + state);
			assertThat(request.content()).isEqualTo("content");
			assertThat(request.updatedAt()).isEqualTo(CREATED_AT);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = " ")
		void refusesBlankContent(String content) {
			Request request = inState(RequestState.CREATED);

			assertThatThrownBy(() -> request.editContent(content, lifecycle, LATER))
					.isInstanceOf(DomainValidationException.class);
			assertThat(request.content()).isEqualTo("content");
		}
	}

	@Nested
	class Transitions {

		@Test
		void returnsAuditEntryForTheTransition() {
			Request request = inState(RequestState.CREATED);

			StateChange change = request.apply(RequestAction.VERIFY, null, lifecycle, NO_NUMBER_EXPECTED, LATER);

			assertThat(change).isEqualTo(new StateChange(1L, RequestAction.VERIFY, RequestState.CREATED,
					RequestState.VERIFIED, null, LATER));
			assertThat(request.state()).isEqualTo(RequestState.VERIFIED);
			assertThat(request.updatedAt()).isEqualTo(LATER);
			assertThat(request.publicationNumber()).isNull();
		}

		@Test
		void publishingAssignsAPublicationNumber() {
			Request request = inState(RequestState.ACCEPTED);

			request.apply(RequestAction.PUBLISH, null, lifecycle, generator, LATER);

			assertThat(request.state()).isEqualTo(RequestState.PUBLISHED);
			assertThat(request.publicationNumber()).isEqualTo(101L);
		}

		@Test
		void eachPublicationGetsItsOwnNumber() {
			Request first = inState(RequestState.ACCEPTED);
			Request second = inState(RequestState.ACCEPTED);

			first.apply(RequestAction.PUBLISH, null, lifecycle, generator, LATER);
			second.apply(RequestAction.PUBLISH, null, lifecycle, generator, LATER);

			assertThat(first.publicationNumber()).isNotEqualTo(second.publicationNumber());
		}

		@ParameterizedTest
		@EnumSource(names = {"CREATED", "VERIFIED", "PUBLISHED", "REJECTED", "DELETED"})
		void forbiddenPublishLeavesRequestUntouchedAndConsumesNoNumber(RequestState state) {
			Request request = inState(state);
			Long numberBefore = request.publicationNumber();

			assertThatThrownBy(() -> request.apply(RequestAction.PUBLISH, null, lifecycle, NO_NUMBER_EXPECTED, LATER))
					.isInstanceOf(InvalidStateTransitionException.class);
			assertThat(request.state()).isEqualTo(state);
			assertThat(request.publicationNumber()).isEqualTo(numberBefore);
			assertThat(request.updatedAt()).isEqualTo(CREATED_AT);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {" ", "\n\t"})
		void deleteRequiresAReason(String reason) {
			Request request = inState(RequestState.CREATED);

			assertThatThrownBy(() -> request.apply(RequestAction.DELETE, reason, lifecycle, generator, LATER))
					.isInstanceOfSatisfying(DomainValidationException.class,
							ex -> assertThat(ex.field()).isEqualTo("reason"))
					.hasMessage("reason is required to delete");
			assertThat(request.state()).isEqualTo(RequestState.CREATED);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = " ")
		void rejectRequiresAReason(String reason) {
			Request request = inState(RequestState.VERIFIED);

			assertThatThrownBy(() -> request.apply(RequestAction.REJECT, reason, lifecycle, generator, LATER))
					.isInstanceOf(DomainValidationException.class);
			assertThat(request.state()).isEqualTo(RequestState.VERIFIED);
		}

		@Test
		void invalidTransitionIsReportedBeforeMissingReason() {
			Request request = inState(RequestState.PUBLISHED);

			assertThatThrownBy(() -> request.apply(RequestAction.REJECT, null, lifecycle, generator, LATER))
					.isInstanceOf(InvalidStateTransitionException.class);
		}

		@Test
		void storesTrimmedReasonOnRequestAndAuditEntry() {
			Request request = inState(RequestState.ACCEPTED);

			StateChange change = request.apply(RequestAction.REJECT, "  out of budget ", lifecycle, generator, LATER);

			assertThat(request.state()).isEqualTo(RequestState.REJECTED);
			assertThat(request.reason()).isEqualTo("out of budget");
			assertThat(change.reason()).isEqualTo("out of budget");
		}

		@Test
		void rejectsOverlongReason() {
			Request request = inState(RequestState.CREATED);
			String reason = "r".repeat(Request.MAX_REASON_LENGTH + 1);

			assertThatThrownBy(() -> request.apply(RequestAction.DELETE, reason, lifecycle, generator, LATER))
					.isInstanceOf(DomainValidationException.class);
		}

		@Test
		void optionalReasonIsKeptForActionsThatDoNotRequireOne() {
			Request request = inState(RequestState.CREATED);

			StateChange change = request.apply(RequestAction.VERIFY, "checked by QA", lifecycle, generator, LATER);

			assertThat(change.reason()).isEqualTo("checked by QA");
		}
	}

	@Test
	void equalityIsBasedOnIdentity() {
		Request a = inState(RequestState.CREATED);
		Request sameId = inState(RequestState.VERIFIED);
		Request unsaved = Request.create("n", "c", lifecycle, CREATED_AT);
		Request otherUnsaved = Request.create("n", "c", lifecycle, CREATED_AT);

		assertThat(a).isEqualTo(sameId).hasSameHashCodeAs(sameId);
		assertThat(unsaved).isEqualTo(unsaved).isNotEqualTo(otherUnsaved).isNotEqualTo(a);
	}
}
