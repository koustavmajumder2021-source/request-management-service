package com.example.requests.domain.exception;

import com.example.requests.domain.RequestState;

public class ContentNotEditableException extends DomainException {

	private final RequestState currentState;

	public ContentNotEditableException(Long id, RequestState currentState) {
		super("Content of request " + id + " cannot be edited in state " + currentState);
		this.currentState = currentState;
	}

	public RequestState currentState() {
		return currentState;
	}
}
