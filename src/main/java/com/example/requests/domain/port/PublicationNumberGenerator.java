package com.example.requests.domain.port;

@FunctionalInterface
public interface PublicationNumberGenerator {

	/**
	 * @return a number that has never been returned before; values are not guaranteed to be consecutive
	 */
	long next();
}
