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

	public static Builder builder(RequestState initialState) {
		return new Builder(initialState);
	}

	public RequestState initialState() {
		return initialState;
	}

	/**
	 * @throws InvalidStateTransitionException if {@code action} is not allowed from {@code from}
	 */
	public RequestState next(RequestState from, RequestAction action) {
		RequestState target = transitions.getOrDefault(from, Map.of()).get(action);
		if (target == null) {
			throw new InvalidStateTransitionException(from, action);
		}
		return target;
	}

	public Set<RequestAction> allowedActions(RequestState from) {
		return Collections.unmodifiableSet(transitions.getOrDefault(from, Map.of()).keySet());
	}

	public boolean isContentEditable(RequestState state) {
		return editableStates.contains(state);
	}

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

		public Builder contentEditableIn(RequestState... states) {
			editableStates.addAll(Set.of(states));
			return this;
		}

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
