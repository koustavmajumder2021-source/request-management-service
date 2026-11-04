package com.example.requests.exception;

public class RequestNotFoundException extends DomainException {

	public RequestNotFoundException(long id) {
		super("Request " + id + " not found");
	}
}
