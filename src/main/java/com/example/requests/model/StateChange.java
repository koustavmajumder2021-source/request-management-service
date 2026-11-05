package com.example.requests.model;

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

	/**
	 * @return the CREATE entry for a request that has just been persisted
	 */
	public static StateChange creationOf(Request request) {
		return new StateChange(request.id(), RequestAction.CREATE, null, request.state(), null, request.createdAt());
	}
}
