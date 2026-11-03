package com.example.requests.domain;

import java.time.Instant;

/**
 * Audit log entry.
 *
 * @param fromState {@code null} for {@code CREATE}
 */
public record StateChange(
		Long requestId,
		RequestAction action,
		RequestState fromState,
		RequestState toState,
		String reason,
		Instant occurredAt) {

	public static StateChange creationOf(Request request) {
		return new StateChange(request.id(), RequestAction.CREATE, null, request.state(), null, request.createdAt());
	}
}
