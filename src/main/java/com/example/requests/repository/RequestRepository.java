package com.example.requests.repository;

import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.Request;
import com.example.requests.model.RequestFilter;

import java.util.Optional;
import java.util.UUID;

public interface RequestRepository {

	/**
	 * Inserts the request if it is new, otherwise updates it.
	 *
	 * @return the stored request
	 */
	Request save(Request request);

	/**
	 * @return the request, or empty if none exists with this id
	 */
	Optional<Request> findById(UUID id);

	/**
	 * Finds the requests matching {@code filter}, ordered by creation time so that paging is stable.
	 */
	PageResult<Request> search(RequestFilter filter, PageQuery pageQuery);
}
