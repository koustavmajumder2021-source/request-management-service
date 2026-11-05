package com.example.requests.controller;

import com.example.requests.config.PaginationProperties;
import com.example.requests.dto.CreateRequestBody;
import com.example.requests.dto.PageResponse;
import com.example.requests.dto.ReasonBody;
import com.example.requests.dto.RequestResponse;
import com.example.requests.dto.StateChangeResponse;
import com.example.requests.dto.UpdateContentBody;
import com.example.requests.model.PageQuery;
import com.example.requests.model.Request;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestFilter;
import com.example.requests.model.RequestLifecycle;
import com.example.requests.model.RequestState;
import com.example.requests.service.RequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
class RequestController {

	private final RequestService service;
	private final RequestLifecycle lifecycle;
	private final PaginationProperties pagination;

	RequestController(RequestService service, RequestLifecycle lifecycle, PaginationProperties pagination) {
		this.service = service;
		this.lifecycle = lifecycle;
		this.pagination = pagination;
	}

	/**
	 * Creates a request in state CREATED and returns it with status 201.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	RequestResponse create(@Valid @RequestBody CreateRequestBody body) {
		return toResponse(service.create(body.name(), body.content()));
	}

	/**
	 * Browses requests page by page, optionally filtered by name and state.
	 *
	 * @param name  case-insensitive substring of the name
	 * @param state exact state
	 * @param page  zero-based page index
	 * @param size  page size; defaults to {@code app.pagination.default-size}, capped at {@code app.pagination.max-size}
	 */
	@GetMapping("/browse")
	PageResponse<RequestResponse> browse(
			@RequestParam(required = false) String name,
			@RequestParam(required = false) RequestState state,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(required = false) @Min(1) Integer size) {
		int pageSize = size == null ? pagination.defaultSize() : Math.min(size, pagination.maxSize());
		return PageResponse.from(service.browse(new RequestFilter(name, state), new PageQuery(page, pageSize))
				.map(this::toResponse));
	}

	/**
	 * Returns a single request, or 404 if it does not exist.
	 */
	@GetMapping("/{id}")
	RequestResponse get(@PathVariable UUID id) {
		return toResponse(service.get(id));
	}

	/**
	 * Replaces the content. Allowed only in CREATED and VERIFIED, otherwise 409.
	 */
	@PatchMapping("/{id}")
	RequestResponse editContent(@PathVariable UUID id, @Valid @RequestBody UpdateContentBody body) {
		return toResponse(service.editContent(id, body.content()));
	}

	/**
	 * CREATED → VERIFIED.
	 */
	@PostMapping("/{id}/verify")
	RequestResponse verify(@PathVariable UUID id) {
		return transition(id, RequestAction.VERIFY, null);
	}

	/**
	 * VERIFIED → ACCEPTED.
	 */
	@PostMapping("/{id}/accept")
	RequestResponse accept(@PathVariable UUID id) {
		return transition(id, RequestAction.ACCEPT, null);
	}

	/**
	 * ACCEPTED → PUBLISHED, assigning the publication number.
	 */
	@PostMapping("/{id}/publish")
	RequestResponse publish(@PathVariable UUID id) {
		return transition(id, RequestAction.PUBLISH, null);
	}

	/**
	 * VERIFIED or ACCEPTED → REJECTED. A reason is mandatory.
	 */
	@PostMapping("/{id}/reject")
	RequestResponse reject(@PathVariable UUID id, @Valid @RequestBody ReasonBody body) {
		return transition(id, RequestAction.REJECT, body.reason());
	}

	/**
	 * CREATED → DELETED. A reason is mandatory. This is a soft delete: the request remains stored and readable.
	 */
	@PostMapping("/{id}/delete")
	RequestResponse delete(@PathVariable UUID id, @Valid @RequestBody ReasonBody body) {
		return transition(id, RequestAction.DELETE, body.reason());
	}

	/**
	 * Returns the audit log of the request, oldest first.
	 */
	@GetMapping("/{id}/audit-log")
	List<StateChangeResponse> auditLog(@PathVariable UUID id) {
		return service.auditLog(id).stream().map(StateChangeResponse::from).toList();
	}

	private RequestResponse transition(UUID id, RequestAction action, String reason) {
		return toResponse(service.transition(id, action, reason));
	}

	private RequestResponse toResponse(Request request) {
		return RequestResponse.from(request, lifecycle);
	}
}
