package com.example.requests.service.impl;

import com.example.requests.exception.RequestNotFoundException;
import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestFilter;
import com.example.requests.model.RequestLifecycle;
import com.example.requests.model.StateChange;
import com.example.requests.repository.PublicationNumberGenerator;
import com.example.requests.repository.RequestRepository;
import com.example.requests.repository.StateChangeLog;
import com.example.requests.service.RequestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Each use case runs in a single transaction so that a state change and its audit entry are
 * committed atomically.
 */
@Service
@Transactional
public class RequestServiceImpl implements RequestService {

	private final RequestRepository requests;
	private final StateChangeLog stateChangeLog;
	private final PublicationNumberGenerator publicationNumbers;
	private final RequestLifecycle lifecycle;
	private final Clock clock;

	public RequestServiceImpl(RequestRepository requests, StateChangeLog stateChangeLog,
			PublicationNumberGenerator publicationNumbers, RequestLifecycle lifecycle, Clock clock) {
		this.requests = requests;
		this.stateChangeLog = stateChangeLog;
		this.publicationNumbers = publicationNumbers;
		this.lifecycle = lifecycle;
		this.clock = clock;
	}

	@Override
	public Request create(String name, String content) {
		Request saved = requests.save(Request.create(name, content, lifecycle, clock.instant()));
		stateChangeLog.append(StateChange.creationOf(saved));

		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public Request get(String name) {
		return load(name);
	}

	@Override
	public Request editContent(String name, String content) {
		Request request = load(name);
		request.editContent(content, lifecycle, clock.instant());
		return requests.save(request);
	}

	@Override
	public Request transition(String name, RequestAction action, String reason) {
		Request request = load(name);
		StateChange change = request.apply(action, reason, lifecycle, publicationNumbers, clock.instant());
		Request saved = requests.save(request);
		stateChangeLog.append(change);
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public PageResult<Request> browse(RequestFilter filter, PageQuery pageQuery) {
		return requests.search(filter, pageQuery);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StateChange> auditLog(String name) {
		return stateChangeLog.findByRequestId(load(name).id());
	}

	private Request load(String name) {
		return requests.findByName(name).orElseThrow(() -> new RequestNotFoundException(name));
	}
}
