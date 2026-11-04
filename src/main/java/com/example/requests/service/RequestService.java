package com.example.requests.service;

import com.example.requests.exception.ContentNotEditableException;
import com.example.requests.exception.DomainValidationException;
import com.example.requests.exception.DuplicateRequestNameException;
import com.example.requests.exception.InvalidStateTransitionException;
import com.example.requests.exception.RequestNotFoundException;
import com.example.requests.model.PageQuery;
import com.example.requests.model.PageResult;
import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestFilter;
import com.example.requests.model.StateChange;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;

/**
 * Use cases for managing requests through their lifecycle.
 */
public interface RequestService {

	/**
	 * Creates a request and records the CREATE entry in the audit log.
	 *
	 * @throws DomainValidationException     if the name or content is invalid
	 * @throws DuplicateRequestNameException if a request that is not deleted already has this name
	 */
	Request create(String name, String content);

	/**
	 * Replaces the content of a request. Editing is not a state change, so the audit log is not touched.
	 *
	 * @throws RequestNotFoundException    if no request has this name
	 * @throws ContentNotEditableException if the request is not in an editable state
	 * @throws DomainValidationException   if the content is invalid
	 */
	Request editContent(String name, String content);

	/**
	 * Applies a lifecycle action and appends the resulting audit entry in the same transaction.
	 *
	 * @param reason required for {@link RequestAction#REJECT} and {@link RequestAction#DELETE}
	 * @throws RequestNotFoundException          if no request has this name
	 * @throws InvalidStateTransitionException   if the action is not allowed in the current state
	 * @throws DomainValidationException         if a required reason is missing or invalid
	 * @throws OptimisticLockingFailureException if the request was modified concurrently
	 */
	Request transition(String name, RequestAction action, String reason);

	/**
	 * Lists requests page by page, filtered by name and state.
	 */
	PageResult<Request> browse(RequestFilter filter, PageQuery pageQuery);

	/**
	 * @return the audit log of the request, oldest first
	 * @throws RequestNotFoundException if no request has this name
	 */
	List<StateChange> auditLog(String name);
}
