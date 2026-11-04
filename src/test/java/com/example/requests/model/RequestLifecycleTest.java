package com.example.requests.model;

import com.example.requests.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.example.requests.model.RequestAction.ACCEPT;
import static com.example.requests.model.RequestAction.CREATE;
import static com.example.requests.model.RequestAction.DELETE;
import static com.example.requests.model.RequestAction.PUBLISH;
import static com.example.requests.model.RequestAction.REJECT;
import static com.example.requests.model.RequestAction.VERIFY;
import static com.example.requests.model.RequestState.ACCEPTED;
import static com.example.requests.model.RequestState.CREATED;
import static com.example.requests.model.RequestState.DELETED;
import static com.example.requests.model.RequestState.PUBLISHED;
import static com.example.requests.model.RequestState.REJECTED;
import static com.example.requests.model.RequestState.VERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLifecycleTest {

	private final RequestLifecycle lifecycle = RequestLifecycle.standard();

	static Stream<Arguments> allowedTransitions() {
		return Stream.of(
				Arguments.of(CREATED, VERIFY, VERIFIED),
				Arguments.of(CREATED, DELETE, DELETED),
				Arguments.of(VERIFIED, ACCEPT, ACCEPTED),
				Arguments.of(VERIFIED, REJECT, REJECTED),
				Arguments.of(ACCEPTED, PUBLISH, PUBLISHED),
				Arguments.of(ACCEPTED, REJECT, REJECTED));
	}

	static Stream<Arguments> forbiddenTransitions() {
		Set<List<Object>> allowed = Set.of(
				List.of(CREATED, VERIFY), List.of(CREATED, DELETE), List.of(VERIFIED, ACCEPT),
				List.of(VERIFIED, REJECT), List.of(ACCEPTED, PUBLISH), List.of(ACCEPTED, REJECT));
		List<Arguments> forbidden = new ArrayList<>();
		for (RequestState state : RequestState.values()) {
			for (RequestAction action : RequestAction.values()) {
				if (!allowed.contains(List.of(state, action))) {
					forbidden.add(Arguments.of(state, action));
				}
			}
		}
		assertThat(forbidden).hasSize(30);
		return forbidden.stream();
	}

	@ParameterizedTest(name = "{0} --{1}--> {2}")
	@MethodSource("allowedTransitions")
	void allowsEveryArrowOfTheDiagram(RequestState from, RequestAction action, RequestState to) {
		assertThat(lifecycle.next(from, action)).isEqualTo(to);
		assertThat(lifecycle.allowedActions(from)).contains(action);
	}

	@ParameterizedTest(name = "{0} --{1}--> forbidden")
	@MethodSource("forbiddenTransitions")
	void rejectsEverythingElse(RequestState from, RequestAction action) {
		assertThatThrownBy(() -> lifecycle.next(from, action))
				.isInstanceOfSatisfying(InvalidStateTransitionException.class, ex -> {
					assertThat(ex.currentState()).isEqualTo(from);
					assertThat(ex.action()).isEqualTo(action);
				})
				.hasMessage("Cannot " + action.name().toLowerCase() + " a request in state " + from);
		assertThat(lifecycle.allowedActions(from)).doesNotContain(action);
	}

	@Test
	void startsInCreated() {
		assertThat(lifecycle.initialState()).isEqualTo(CREATED);
	}

	@ParameterizedTest
	@EnumSource(names = {"PUBLISHED", "REJECTED", "DELETED"})
	void endStatesAreTerminal(RequestState state) {
		assertThat(lifecycle.isTerminal(state)).isTrue();
		assertThat(lifecycle.allowedActions(state)).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(names = {"CREATED", "VERIFIED", "ACCEPTED"})
	void intermediateStatesAreNotTerminal(RequestState state) {
		assertThat(lifecycle.isTerminal(state)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(names = {"CREATED", "VERIFIED"})
	void contentIsEditableInCreatedAndVerified(RequestState state) {
		assertThat(lifecycle.isContentEditable(state)).isTrue();
	}

	@ParameterizedTest
	@EnumSource(names = {"ACCEPTED", "PUBLISHED", "REJECTED", "DELETED"})
	void contentIsNotEditableInAnyOtherState(RequestState state) {
		assertThat(lifecycle.isContentEditable(state)).isFalse();
	}

	@Test
	void onlyDeleteAndRejectRequireAReason() {
		assertThat(Stream.of(RequestAction.values()).filter(RequestAction::isReasonRequired))
				.containsExactlyInAnyOrder(DELETE, REJECT);
	}

	@Test
	void exposedCollectionsAreImmutable() {
		assertThatThrownBy(() -> lifecycle.allowedActions(CREATED).clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void builderSupportsACustomLifecycle() {
		RequestLifecycle custom = RequestLifecycle.builder(CREATED)
				.transition(CREATED, PUBLISH, PUBLISHED)
				.build();

		assertThat(custom.next(CREATED, PUBLISH)).isEqualTo(PUBLISHED);
		assertThat(custom.isContentEditable(CREATED)).isFalse();
	}

	@Test
	void builtLifecycleIsNotAffectedByLaterBuilderChanges() {
		RequestLifecycle.Builder builder = RequestLifecycle.builder(CREATED).transition(CREATED, VERIFY, VERIFIED);
		RequestLifecycle built = builder.build();

		builder.transition(CREATED, DELETE, DELETED).contentEditableIn(CREATED);

		assertThat(built.allowedActions(CREATED)).containsExactly(VERIFY);
		assertThat(built.isContentEditable(CREATED)).isFalse();
	}

	@Test
	void builderRejectsDuplicateTransitions() {
		RequestLifecycle.Builder builder = RequestLifecycle.builder(CREATED).transition(CREATED, VERIFY, VERIFIED);

		assertThatThrownBy(() -> builder.transition(CREATED, VERIFY, ACCEPTED))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Duplicate");
	}

	@Test
	void builderRejectsCreateAsATransition() {
		assertThatThrownBy(() -> RequestLifecycle.builder(CREATED).transition(VERIFIED, CREATE, CREATED))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void builderRejectsNulls() {
		assertThatThrownBy(() -> RequestLifecycle.builder(null)).isInstanceOf(NullPointerException.class);
		RequestLifecycle.Builder builder = RequestLifecycle.builder(CREATED);
		assertThatThrownBy(() -> builder.transition(null, VERIFY, VERIFIED)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> builder.transition(CREATED, null, VERIFIED)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> builder.transition(CREATED, VERIFY, null)).isInstanceOf(NullPointerException.class);
	}
}
