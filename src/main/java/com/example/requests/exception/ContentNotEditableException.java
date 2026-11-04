package com.example.requests.exception;

import com.example.requests.model.RequestState;

public class ContentNotEditableException extends DomainException {

	private final RequestState currentState;

	public ContentNotEditableException(RequestState currentState) {
		super("Content cannot be edited in state " + currentState);
		this.currentState = currentState;
	}

	public RequestState currentState() {
		return currentState;
	}
}
