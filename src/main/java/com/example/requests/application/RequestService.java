package com.example.requests.application;

import com.example.requests.domain.PageQuery;
import com.example.requests.domain.PageResult;
import com.example.requests.domain.Request;
import com.example.requests.domain.RequestAction;
import com.example.requests.domain.RequestFilter;
import com.example.requests.domain.RequestLifecycle;
import com.example.requests.domain.StateChange;
import com.example.requests.domain.exception.RequestNotFoundException;
import com.example.requests.domain.port.PublicationNumberGenerator;
import com.example.requests.domain.port.RequestRepository;
import com.example.requests.domain.port.StateChangeLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Application service. Each use case runs in a single transaction so that a state change
 * and its audit entry are committed atomically.
 */
@Service
@Transactional
public class RequestService {

	private final RequestRepository requests;
	private final StateChangeLog stateChangeLog;
	private final PublicationNumberGenerator publicationNumbers;
	private final RequestLifecycle lifecycle;
	private final Clock clock;

	public RequestService(RequestRepository requests, StateChangeLog stateChangeLog,
			PublicationNumberGenerator publicationNumbers, RequestLifecycle lifecycle, Clock clock) {
		this.requests = requests;
		this.stateChangeLog = stateChangeLog;
		this.publicationNumbers = publicationNumbers;
		this.lifecycle = lifecycle;
		this.clock = clock;
	}

	public Request create(String name, String content) {
		Request saved = requests.save(Request.create(name, content, lifecycle, clock.instant()));
		stateChangeLog.append(StateChange.creationOf(saved));
		return saved;
	}

	@Transactional(readOnly = true)
	public Request get(long id) {
		return load(id);
	}

	public Request editContent(long id, String content) {
		Request request = load(id);
		request.editContent(content, lifecycle, clock.instant());
		return requests.save(request);
	}

	public Request transition(long id, RequestAction action, String reason) {
		Request request = load(id);
		StateChange change = request.apply(action, reason, lifecycle, publicationNumbers, clock.instant());
		Request saved = requests.save(request);
		stateChangeLog.append(change);
		return saved;
	}

	@Transactional(readOnly = true)
	public PageResult<Request> search(RequestFilter filter, PageQuery pageQuery) {
		return requests.search(filter, pageQuery);
	}

	@Transactional(readOnly = true)
	public List<StateChange> history(long id) {
		load(id);
		return stateChangeLog.findByRequestId(id);
	}

	private Request load(long id) {
		return requests.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
	}
}
