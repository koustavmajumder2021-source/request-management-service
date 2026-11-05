package com.example.requests.dto;

import com.example.requests.model.PageResult;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public static <T> PageResponse<T> from(PageResult<T> result) {
		return new PageResponse<>(result.items(), result.page(), result.size(), result.totalItems(),
				result.totalPages());
	}
}
