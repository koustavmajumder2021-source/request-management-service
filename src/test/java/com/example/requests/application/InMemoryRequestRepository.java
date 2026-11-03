package com.example.requests.application;

import com.example.requests.domain.PageQuery;
import com.example.requests.domain.PageResult;
import com.example.requests.domain.Request;
import com.example.requests.domain.RequestFilter;
import com.example.requests.domain.port.RequestRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

class InMemoryRequestRepository implements RequestRepository {

	private final Map<Long, Request> store = new TreeMap<>();
	private long nextId = 1;

	@Override
	public Request save(Request request) {
		Request stored = request.id() != null ? request
				: Request.restore(nextId++, request.name(), request.content(), request.state(),
						request.publicationNumber(), request.reason(), request.createdAt(), request.updatedAt());
		store.put(stored.id(), stored);
		return stored;
	}

	@Override
	public Optional<Request> findById(long id) {
		return Optional.ofNullable(store.get(id));
	}

	@Override
	public PageResult<Request> search(RequestFilter filter, PageQuery pageQuery) {
		List<Request> matching = store.values().stream()
				.filter(r -> filter.name() == null || r.name().toLowerCase().contains(filter.name().toLowerCase()))
				.filter(r -> filter.state() == null || r.state() == filter.state())
				.sorted(Comparator.comparing(Request::id))
				.toList();
		List<Request> page = matching.stream()
				.skip((long) pageQuery.page() * pageQuery.size())
				.limit(pageQuery.size())
				.toList();
		return new PageResult<>(page, pageQuery.page(), pageQuery.size(), matching.size());
	}
}
