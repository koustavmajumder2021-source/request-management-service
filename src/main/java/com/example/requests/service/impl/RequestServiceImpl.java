package com.example.requests.service.impl;

import com.example.requests.exception.DuplicateRequestNameException;
import com.example.requests.exception.RequestNotFoundException;
import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestFilter;
import com.example.requests.model.RequestLifecycle;
import com.example.requests.model.RequestState;
import com.example.requests.model.StateChange;
import com.example.requests.repository.PublicationNumberGenerator;
import com.example.requests.repository.RequestRepository;
import com.example.requests.repository.StateChangeLog;
import com.example.requests.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
		Request request = Request.create(name, content, lifecycle, clock.instant());
		if (requests.existsActiveByName(request.name())) {
			throw new DuplicateRequestNameException(request.name());
		}
		Request saved = requests.save(request);
		stateChangeLog.append(StateChange.creationOf(saved));
		log.info("Created request '{}' (id {})", saved.name(), saved.id());

		return saved;
	}

	@Override
	public Request editContent(String name, String content) {
		Request request = load(name);
		request.editContent(content, lifecycle, clock.instant());
		Request saved = requests.save(request);
		log.info("Updated content of request '{}'", name);
		return saved;
	}

	@Override
	public Request transition(String name, RequestAction action, String reason) {
		Request request = load(name);
		StateChange change = request.apply(action, reason, lifecycle, publicationNumbers, clock.instant());
		Request saved = requests.save(request);
		stateChangeLog.append(change);
		if (change.toState() == RequestState.PUBLISHED) {
			log.info("Published request '{}' with publication number {}", name, saved.publicationNumber());
		}
		else {
			log.info("Request '{}' moved from {} to {} ({})", name, change.fromState(), change.toState(), action);
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
	public List<StateChange> auditLog(String name) {
		log.debug("Reading audit log of request '{}'", name);
		return stateChangeLog.findByRequestId(load(name).id());
	}

	private Request load(String name) {
		return requests.findByName(name).orElseThrow(() -> new RequestNotFoundException(name));
	}
}
