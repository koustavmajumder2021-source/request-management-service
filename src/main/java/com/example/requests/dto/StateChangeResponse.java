package com.example.requests.dto;

import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestState;
import com.example.requests.model.StateChange;

import java.time.Instant;

public record StateChangeResponse(RequestAction action, RequestState fromState, RequestState toState, String reason,
		Instant occurredAt) {

	public static StateChangeResponse from(StateChange c) {
		return new StateChangeResponse(c.action(), c.fromState(), c.toState(), c.reason(), c.occurredAt());
	}
}
