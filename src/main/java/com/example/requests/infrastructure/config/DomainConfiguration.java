package com.example.requests.infrastructure.config;

import com.example.requests.domain.RequestLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class DomainConfiguration {

	/**
	 * The lifecycle from the specification. Overriding this bean changes the state diagram for the
	 * whole application without touching the domain model.
	 */
	@Bean
	RequestLifecycle requestLifecycle() {
		return RequestLifecycle.standard();
	}

	/**
	 * UTC system clock, injected wherever the current time is needed so that tests can fix it.
	 */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
