package com.example.requests.model;

import java.util.Objects;

/** Zero-based page request. */
public final class PageQuery {

	private final int page;
	private final int size;

	public PageQuery(int page, int size) {
		if (page < 0) {
			throw new IllegalArgumentException("page must be >= 0");
		}
		if (size < 1) {
			throw new IllegalArgumentException("size must be >= 1");
		}
		this.page = page;
		this.size = size;
	}

	public int page() {
		return page;
	}

	public int size() {
		return size;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof PageQuery other && page == other.page && size == other.size);
	}

	@Override
	public int hashCode() {
		return Objects.hash(page, size);
	}

	@Override
	public String toString() {
		return "PageQuery[page=" + page + ", size=" + size + "]";
	}
}
