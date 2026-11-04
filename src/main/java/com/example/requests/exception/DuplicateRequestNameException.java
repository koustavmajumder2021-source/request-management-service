package com.example.requests.exception;

/** Request names are unique, including those of deleted and rejected requests. */
public class DuplicateRequestNameException extends DomainException {

	public DuplicateRequestNameException(String name) {
		super("A request named '" + name + "' already exists");
	}
}
