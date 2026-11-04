package com.example.requests.dto;

import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestLifecycle;
import com.example.requests.model.RequestState;

import java.time.Instant;
import java.util.Set;

public class RequestResponse {

	private final long id;
	private final String name;
	private final String content;
	private final RequestState state;
	private final Long publicationNumber;
	private final String reason;
	private final Set<RequestAction> allowedActions;
	private final boolean contentEditable;
	private final Instant createdAt;
	private final Instant updatedAt;

	public RequestResponse(long id, String name, String content, RequestState state, Long publicationNumber,
			String reason, Set<RequestAction> allowedActions, boolean contentEditable, Instant createdAt,
			Instant updatedAt) {
		this.id = id;
		this.name = name;
		this.content = content;
		this.state = state;
		this.publicationNumber = publicationNumber;
		this.reason = reason;
		this.allowedActions = allowedActions;
		this.contentEditable = contentEditable;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static RequestResponse from(Request r, RequestLifecycle lifecycle) {
		return new RequestResponse(r.id(), r.name(), r.content(), r.state(), r.publicationNumber(), r.reason(),
				lifecycle.allowedActions(r.state()), lifecycle.isContentEditable(r.state()),
				r.createdAt(), r.updatedAt());
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getContent() {
		return content;
	}

	public RequestState getState() {
		return state;
	}

	public Long getPublicationNumber() {
		return publicationNumber;
	}

	public String getReason() {
		return reason;
	}

	public Set<RequestAction> getAllowedActions() {
		return allowedActions;
	}

	public boolean isContentEditable() {
		return contentEditable;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
