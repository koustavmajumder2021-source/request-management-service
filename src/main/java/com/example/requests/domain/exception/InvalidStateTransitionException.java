package com.example.requests.domain.exception;

import com.example.requests.domain.RequestAction;
import com.example.requests.domain.RequestState;

public class InvalidStateTransitionException extends DomainException {

	private final RequestState currentState;
	private final RequestAction action;

	public InvalidStateTransitionException(RequestState currentState, RequestAction action) {
		super("Cannot " + action.name().toLowerCase() + " a request in state " + currentState);
		this.currentState = currentState;
		this.action = action;
	}

	public RequestState currentState() {
		return currentState;
	}

	public RequestAction action() {
		return action;
	}
}
