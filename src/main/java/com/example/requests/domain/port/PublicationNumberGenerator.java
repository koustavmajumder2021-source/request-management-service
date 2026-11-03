package com.example.requests.domain.port;

@FunctionalInterface
public interface PublicationNumberGenerator {

	long next();
}
