package com.example.requests.service.impl;

import com.example.requests.exception.RequestNotFoundException;
import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.PublicationNumberGenerator;
import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestFilter;
import com.example.requests.model.RequestLifecycle;
import com.example.requests.model.RequestState;
import com.example.requests.model.StateChange;
import com.example.requests.repository.RequestRepository;
import com.example.requests.repository.StateChangeLog;
import com.example.requests.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Each use case runs in a single transaction so that a state change and its audit entry are
 * committed atomically.
 */
@Service
@Transactional
public class RequestServiceImpl implements RequestService {

	private static final Logger log = LoggerFactory.getLogger(RequestServiceImpl.class);

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
		log.info("Created request {} ('{}')", saved.id(), saved.name());

		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public Request get(UUID id) {
		return load(id);
	}

	@Override
	public Request editContent(UUID id, String content) {
		Request request = load(id);
		request.editContent(content, lifecycle, clock.instant());
		Request saved = requests.save(request);
		log.info("Updated content of request {}", id);
		return saved;
	}

	@Override
	public Request transition(UUID id, RequestAction action, String reason) {
		Request request = load(id);
		StateChange change = request.apply(action, reason, lifecycle, publicationNumbers, clock.instant());
		Request saved = requests.save(request);
		stateChangeLog.append(change);
		if (change.toState() == RequestState.PUBLISHED) {
			log.info("Published request {} with publication number {}", id, saved.publicationNumber());
		}
		else {
			log.info("Request {} moved from {} to {} ({})", id, change.fromState(), change.toState(), action);
		}
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public PageResult<Request> browse(RequestFilter filter, PageQuery pageQuery) {
		log.debug("Browsing requests with {} and {}", filter, pageQuery);
		return requests.search(filter, pageQuery);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StateChange> auditLog(UUID id) {
		log.debug("Reading audit log of request {}", id);
		return stateChangeLog.findByRequestId(load(id).id());
	}

	private Request load(UUID id) {
		return requests.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
	}
}
