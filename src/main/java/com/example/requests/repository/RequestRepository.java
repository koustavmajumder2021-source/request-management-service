package com.example.requests.repository;

import com.example.requests.exception.DuplicateRequestNameException;
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
	 * @throws DuplicateRequestNameException if another request that is not deleted has the same name
	 */
	Request save(Request request);

	/**
	 * @return the current request with this name; if the name only belongs to deleted requests, the most
	 *         recently deleted one; empty if no request ever had this name
	 */
	Optional<Request> findByName(String name);

	/**
	 * Finds the requests matching {@code filter}, ordered by id so that paging is stable.
	 */
	PageResult<Request> search(RequestFilter filter, PageQuery pageQuery);
}
