package com.example.requests.repository;

import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.Request;
import com.example.requests.model.RequestFilter;

import java.util.Optional;

public interface RequestRepository {

	/**
	 * Inserts a new request (id {@code null}) or updates an existing one.
	 *
	 * @return the stored request, carrying the generated id for new requests
	 */
	Request save(Request request);

	/**
	 * @return the request, or empty if none exists with this id
	 */
	Optional<Request> findById(long id);

	/**
	 * Finds the requests matching {@code filter}, ordered by id so that paging is stable.
	 */
	PageResult<Request> search(RequestFilter filter, PageQuery pageQuery);
}
