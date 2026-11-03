package com.example.requests.domain.exception;

public class RequestNotFoundException extends DomainException {

	public RequestNotFoundException(long id) {
		super("Request " + id + " not found");
	}
}
