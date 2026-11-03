package com.example.requests.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param defaultSize used when the client sends no {@code size}
 * @param maxSize     larger requested sizes are capped to this
 */
@ConfigurationProperties("app.pagination")
public record PaginationProperties(@DefaultValue("10") int defaultSize, @DefaultValue("100") int maxSize) {

	public PaginationProperties {
		if (defaultSize < 1 || maxSize < defaultSize) {
			throw new IllegalArgumentException("Require 1 <= app.pagination.default-size <= app.pagination.max-size");
		}
	}
}
