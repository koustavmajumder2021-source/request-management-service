package com.example.requests.repository;

@FunctionalInterface
public interface PublicationNumberGenerator {

	/**
	 * @return a number that has never been returned before; values are not guaranteed to be consecutive
	 */
	long next();
}
