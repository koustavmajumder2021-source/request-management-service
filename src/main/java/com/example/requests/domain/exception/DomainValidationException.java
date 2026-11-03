package com.example.requests.domain.exception;

public class DomainValidationException extends DomainException {

	private final String field;

	public DomainValidationException(String field, String message) {
		super(field + " " + message);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
