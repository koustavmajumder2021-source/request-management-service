package com.example.requests.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class PageResult<T> {

	private final List<T> items;
	private final int page;
	private final int size;
	private final long totalItems;

	public PageResult(List<T> items, int page, int size, long totalItems) {
		this.items = List.copyOf(items);
		this.page = page;
		this.size = size;
		this.totalItems = totalItems;
	}

	public List<T> items() {
		return items;
	}

	public int page() {
		return page;
	}

	public int size() {
		return size;
	}

	public long totalItems() {
		return totalItems;
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

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof PageResult<?> other && page == other.page && size == other.size
				&& totalItems == other.totalItems && items.equals(other.items));
	}

	@Override
	public int hashCode() {
		return Objects.hash(items, page, size, totalItems);
	}

	@Override
	public String toString() {
		return "PageResult[items=" + items + ", page=" + page + ", size=" + size + ", totalItems=" + totalItems + "]";
	}
}
