package com.example.requests.dto;

import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestLifecycle;
import com.example.requests.model.RequestState;

import java.time.Instant;
import java.util.Set;

public record RequestResponse(
		long id,
		String name,
		String content,
		RequestState state,
		Long publicationNumber,
		String reason,
		Set<RequestAction> allowedActions,
		boolean contentEditable,
		Instant createdAt,
		Instant updatedAt) {

	public static RequestResponse from(Request r, RequestLifecycle lifecycle) {
		return new RequestResponse(r.id(), r.name(), r.content(), r.state(), r.publicationNumber(), r.reason(),
				lifecycle.allowedActions(r.state()), lifecycle.isContentEditable(r.state()),
				r.createdAt(), r.updatedAt());
	}
}
