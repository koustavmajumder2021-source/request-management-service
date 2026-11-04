package com.example.requests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.pagination")
public class PaginationProperties {

	private final int defaultSize;
	private final int maxSize;

	/**
	 * @param defaultSize used when the client sends no {@code size}
	 * @param maxSize     larger requested sizes are capped to this
	 */
	public PaginationProperties(@DefaultValue("10") int defaultSize, @DefaultValue("100") int maxSize) {
		if (defaultSize < 1 || maxSize < defaultSize) {
			throw new IllegalArgumentException("Require 1 <= app.pagination.default-size <= app.pagination.max-size");
		}
		this.defaultSize = defaultSize;
		this.maxSize = maxSize;
	}

	public int getDefaultSize() {
		return defaultSize;
	}

	public int getMaxSize() {
		return maxSize;
	}
}
