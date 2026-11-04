package com.example.requests.dto;

import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestState;
import com.example.requests.model.StateChange;

import java.time.Instant;

public class StateChangeResponse {

	private final RequestAction action;
	private final RequestState fromState;
	private final RequestState toState;
	private final String reason;
	private final Instant occurredAt;

	public StateChangeResponse(RequestAction action, RequestState fromState, RequestState toState, String reason,
			Instant occurredAt) {
		this.action = action;
		this.fromState = fromState;
		this.toState = toState;
		this.reason = reason;
		this.occurredAt = occurredAt;
	}

	public static StateChangeResponse from(StateChange c) {
		return new StateChangeResponse(c.action(), c.fromState(), c.toState(), c.reason(), c.occurredAt());
	}

	public RequestAction getAction() {
		return action;
	}

	public RequestState getFromState() {
		return fromState;
	}

	public RequestState getToState() {
		return toState;
	}

	public String getReason() {
		return reason;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
