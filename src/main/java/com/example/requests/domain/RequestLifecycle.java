package com.example.requests.domain;

import com.example.requests.domain.exception.InvalidStateTransitionException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable transition table for requests, plus the states in which content is editable.
 * {@link #standard()} encodes the specified state diagram.
 */
public final class RequestLifecycle {

	private final RequestState initialState;
	private final Map<RequestState, Map<RequestAction, RequestState>> transitions;
	private final Set<RequestState> editableStates;

	private RequestLifecycle(RequestState initialState,
			Map<RequestState, Map<RequestAction, RequestState>> transitions,
			Set<RequestState> editableStates) {
		this.initialState = initialState;
		this.transitions = transitions;
		this.editableStates = editableStates;
	}

	/**
	 * Returns the lifecycle defined by the specification: CREATED → VERIFIED → ACCEPTED → PUBLISHED,
	 * DELETE from CREATED and REJECT from VERIFIED or ACCEPTED. Content is editable in CREATED and VERIFIED.
	 */
	public static RequestLifecycle standard() {
		return builder(RequestState.CREATED)
				.transition(RequestState.CREATED, RequestAction.VERIFY, RequestState.VERIFIED)
				.transition(RequestState.CREATED, RequestAction.DELETE, RequestState.DELETED)
				.transition(RequestState.VERIFIED, RequestAction.ACCEPT, RequestState.ACCEPTED)
				.transition(RequestState.VERIFIED, RequestAction.REJECT, RequestState.REJECTED)
				.transition(RequestState.ACCEPTED, RequestAction.PUBLISH, RequestState.PUBLISHED)
				.transition(RequestState.ACCEPTED, RequestAction.REJECT, RequestState.REJECTED)
				.contentEditableIn(RequestState.CREATED, RequestState.VERIFIED)
				.build();
	}

	/**
	 * Starts the definition of a custom lifecycle.
	 *
	 * @param initialState state assigned to newly created requests
	 */
	public static Builder builder(RequestState initialState) {
		return new Builder(initialState);
	}

	/**
	 * @return the state every new request starts in
	 */
	public RequestState initialState() {
		return initialState;
	}

	/**
	 * Resolves the target state of a transition.
	 *
	 * @param from   current state
	 * @param action action to apply
	 * @return the state the request moves to
	 * @throws InvalidStateTransitionException if {@code action} is not allowed from {@code from}
	 */
	public RequestState next(RequestState from, RequestAction action) {
		RequestState target = transitions.getOrDefault(from, Map.of()).get(action);
		if (target == null) {
			throw new InvalidStateTransitionException(from, action);
		}
		return target;
	}

	/**
	 * @return the actions permitted from {@code from} as an unmodifiable set; empty for terminal states
	 */
	public Set<RequestAction> allowedActions(RequestState from) {
		return Collections.unmodifiableSet(transitions.getOrDefault(from, Map.of()).keySet());
	}

	/**
	 * @return whether the content of a request in {@code state} may be modified
	 */
	public boolean isContentEditable(RequestState state) {
		return editableStates.contains(state);
	}

	/**
	 * @return whether no further action is possible from {@code state}
	 */
	public boolean isTerminal(RequestState state) {
		return allowedActions(state).isEmpty();
	}

	public static final class Builder {

		private final RequestState initialState;
		private final Map<RequestState, Map<RequestAction, RequestState>> transitions = new EnumMap<>(RequestState.class);
		private final Set<RequestState> editableStates = EnumSet.noneOf(RequestState.class);

		private Builder(RequestState initialState) {
			this.initialState = Objects.requireNonNull(initialState, "initialState");
		}

		/**
		 * Adds the transition {@code from --action--> to}.
		 *
		 * @throws IllegalArgumentException if the transition is already defined or {@code action} is {@code CREATE}
		 */
		public Builder transition(RequestState from, RequestAction action, RequestState to) {
			Objects.requireNonNull(from, "from");
			Objects.requireNonNull(action, "action");
			Objects.requireNonNull(to, "to");
			if (action == RequestAction.CREATE) {
				throw new IllegalArgumentException("CREATE is the initial transition and cannot leave a state");
			}
			RequestState previous = transitions.computeIfAbsent(from, s -> new EnumMap<>(RequestAction.class))
					.putIfAbsent(action, to);
			if (previous != null) {
				throw new IllegalArgumentException("Duplicate transition " + from + " --" + action + "-->");
			}
			return this;
		}

		/**
		 * Marks the states in which the content of a request may be edited.
		 */
		public Builder contentEditableIn(RequestState... states) {
			editableStates.addAll(Set.of(states));
			return this;
		}

		/**
		 * @return an immutable lifecycle; later changes to this builder do not affect it
		 */
		public RequestLifecycle build() {
			Map<RequestState, Map<RequestAction, RequestState>> copy = new EnumMap<>(RequestState.class);
			transitions.forEach((state, actions) -> copy.put(state, Collections.unmodifiableMap(new EnumMap<>(actions))));
			Set<RequestState> editable = editableStates.isEmpty()
					? Collections.emptySet()
					: Collections.unmodifiableSet(EnumSet.copyOf(editableStates));
			return new RequestLifecycle(initialState, Collections.unmodifiableMap(copy), editable);
		}
	}
}
