package com.example.requests.exception;

import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestState;

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
