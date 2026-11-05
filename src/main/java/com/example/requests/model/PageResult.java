package com.example.requests.model;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

	public PageResult {
		items = List.copyOf(items);
	}

	/**
	 * @return the number of pages needed for {@code totalItems}; 0 when there are no results
	 */
	public int totalPages() {
		return (int) ((totalItems + size - 1) / size);
	}

	/**
	 * Transforms the items while keeping the paging metadata.
	 */
	public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
		return new PageResult<>(items.stream().<R>map(mapper).toList(), page, size, totalItems);
	}
}
