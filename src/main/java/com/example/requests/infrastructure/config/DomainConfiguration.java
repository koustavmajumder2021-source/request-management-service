package com.example.requests.infrastructure.config;

import com.example.requests.domain.RequestLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class DomainConfiguration {

	@Bean
	RequestLifecycle requestLifecycle() {
		return RequestLifecycle.standard();
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
