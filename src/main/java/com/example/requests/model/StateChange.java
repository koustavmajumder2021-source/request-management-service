package com.example.requests.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entry.
 *
 * @param fromState {@code null} for {@code CREATE}
 */
public record StateChange(
		UUID requestId,
		RequestAction action,
		RequestState fromState,
		RequestState toState,
		String reason,
		Instant occurredAt) {

	/**
	 * @return the CREATE entry for a newly created request
	 */
	public static StateChange creationOf(Request request) {
		return new StateChange(request.id(), RequestAction.CREATE, null, request.state(), null, request.createdAt());
	}
}
