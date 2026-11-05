package com.example.requests.model;

/**
 * @param name  case-insensitive substring match; {@code null} matches all
 * @param state exact match; {@code null} matches all
 */
public record RequestFilter(String name, RequestState state) {

	public RequestFilter {
		name = name == null || name.isBlank() ? null : name.strip();
	}
}
