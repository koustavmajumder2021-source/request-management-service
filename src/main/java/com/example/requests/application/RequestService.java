package com.example.requests.application;

import com.example.requests.domain.PageQuery;
import com.example.requests.domain.PageResult;
import com.example.requests.domain.Request;
import com.example.requests.domain.RequestAction;
import com.example.requests.domain.RequestFilter;
import com.example.requests.domain.RequestLifecycle;
import com.example.requests.domain.StateChange;
import com.example.requests.domain.exception.ContentNotEditableException;
import com.example.requests.domain.exception.DomainValidationException;
import com.example.requests.domain.exception.InvalidStateTransitionException;
import com.example.requests.domain.exception.RequestNotFoundException;
import com.example.requests.domain.port.PublicationNumberGenerator;
import com.example.requests.domain.port.RequestRepository;
import com.example.requests.domain.port.StateChangeLog;
import org.springframework.dao.OptimisticLockingFailureException;
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

	/**
	 * Creates a request and records the CREATE entry in the audit log.
	 *
	 * @throws DomainValidationException if the name or content is invalid
	 */
	public Request create(String name, String content) {
		Request saved = requests.save(Request.create(name, content, lifecycle, clock.instant()));
		stateChangeLog.append(StateChange.creationOf(saved));
		return saved;
	}

	/**
	 * @throws RequestNotFoundException if no request has this id
	 */
	@Transactional(readOnly = true)
	public Request get(long id) {
		return load(id);
	}

	/**
	 * Replaces the content of a request. Editing is not a state change, so the audit log is not touched.
	 *
	 * @throws RequestNotFoundException    if no request has this id
	 * @throws ContentNotEditableException if the request is not in an editable state
	 * @throws DomainValidationException   if the content is invalid
	 */
	public Request editContent(long id, String content) {
		Request request = load(id);
		request.editContent(content, lifecycle, clock.instant());
		return requests.save(request);
	}

	/**
	 * Applies a lifecycle action and appends the resulting audit entry in the same transaction.
	 *
	 * @param reason required for {@link RequestAction#REJECT} and {@link RequestAction#DELETE}
	 * @throws RequestNotFoundException            if no request has this id
	 * @throws InvalidStateTransitionException     if the action is not allowed in the current state
	 * @throws DomainValidationException           if a required reason is missing or invalid
	 * @throws OptimisticLockingFailureException   if the request was modified concurrently
	 */
	public Request transition(long id, RequestAction action, String reason) {
		Request request = load(id);
		StateChange change = request.apply(action, reason, lifecycle, publicationNumbers, clock.instant());
		Request saved = requests.save(request);
		stateChangeLog.append(change);
		return saved;
	}

	/**
	 * Lists requests page by page, filtered by name and state.
	 */
	@Transactional(readOnly = true)
	public PageResult<Request> search(RequestFilter filter, PageQuery pageQuery) {
		return requests.search(filter, pageQuery);
	}

	/**
	 * @return the audit log of the request, oldest first
	 * @throws RequestNotFoundException if no request has this id
	 */
	@Transactional(readOnly = true)
	public List<StateChange> history(long id) {
		load(id);
		return stateChangeLog.findByRequestId(id);
	}

	private Request load(long id) {
		return requests.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
	}
}
