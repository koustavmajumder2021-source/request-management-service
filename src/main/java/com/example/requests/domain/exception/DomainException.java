package com.example.requests.domain.exception;

public abstract class DomainException extends RuntimeException {

	protected DomainException(String message) {
		super(message);
	}
}
