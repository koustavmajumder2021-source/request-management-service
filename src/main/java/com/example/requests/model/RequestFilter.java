package com.example.requests.model;

import java.util.Objects;

public final class RequestFilter {

	private final String name;
	private final RequestState state;

	/**
	 * @param name  case-insensitive substring match; {@code null} or blank matches all
	 * @param state exact match; {@code null} matches all
	 */
	public RequestFilter(String name, RequestState state) {
		this.name = name == null || name.isBlank() ? null : name.strip();
		this.state = state;
	}

	public String name() {
		return name;
	}

	public RequestState state() {
		return state;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof RequestFilter other && Objects.equals(name, other.name) && state == other.state);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, state);
	}

	@Override
	public String toString() {
		return "RequestFilter[name=" + name + ", state=" + state + "]";
	}
}
