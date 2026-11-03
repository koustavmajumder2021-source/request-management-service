package com.example.requests.domain.port;

import com.example.requests.domain.PageQuery;
import com.example.requests.domain.PageResult;
import com.example.requests.domain.Request;
import com.example.requests.domain.RequestFilter;

import java.util.Optional;

public interface RequestRepository {

	Request save(Request request);

	Optional<Request> findById(long id);

	/** Ordered by id so that paging is stable. */
	PageResult<Request> search(RequestFilter filter, PageQuery pageQuery);
}
