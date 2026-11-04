package com.example.requests.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit log entry.
 */
public final class StateChange {

	private final Long requestId;
	private final RequestAction action;
	private final RequestState fromState;
	private final RequestState toState;
	private final String reason;
	private final Instant occurredAt;

	/**
	 * @param fromState {@code null} for {@code CREATE}
	 */
	public StateChange(Long requestId, RequestAction action, RequestState fromState, RequestState toState,
			String reason, Instant occurredAt) {
		this.requestId = requestId;
		this.action = action;
		this.fromState = fromState;
		this.toState = toState;
		this.reason = reason;
		this.occurredAt = occurredAt;
	}

	/**
	 * @return the CREATE entry for a request that has just been persisted
	 */
	public static StateChange creationOf(Request request) {
		return new StateChange(request.id(), RequestAction.CREATE, null, request.state(), null, request.createdAt());
	}

	public Long requestId() {
		return requestId;
	}

	public RequestAction action() {
		return action;
	}

	public RequestState fromState() {
		return fromState;
	}

	public RequestState toState() {
		return toState;
	}

	public String reason() {
		return reason;
	}

	public Instant occurredAt() {
		return occurredAt;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof StateChange other && Objects.equals(requestId, other.requestId)
				&& action == other.action && fromState == other.fromState && toState == other.toState
				&& Objects.equals(reason, other.reason) && Objects.equals(occurredAt, other.occurredAt));
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestId, action, fromState, toState, reason, occurredAt);
	}

	@Override
	public String toString() {
		return "StateChange[requestId=" + requestId + ", action=" + action + ", fromState=" + fromState
				+ ", toState=" + toState + ", reason=" + reason + ", occurredAt=" + occurredAt + "]";
	}
}
