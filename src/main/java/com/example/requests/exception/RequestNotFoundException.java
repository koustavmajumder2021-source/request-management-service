package com.example.requests.exception;

import java.util.UUID;

public class RequestNotFoundException extends DomainException {

	public RequestNotFoundException(UUID id) {
		super("Request " + id + " not found");
	}
}
