package com.example.requests.exception;

public class RequestNotFoundException extends DomainException {

	public RequestNotFoundException(String name) {
		super("Request '" + name + "' not found");
	}
}
