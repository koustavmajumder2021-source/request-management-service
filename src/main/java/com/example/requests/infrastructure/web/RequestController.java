package com.example.requests.infrastructure.web;

import com.example.requests.application.RequestService;
import com.example.requests.domain.PageQuery;
import com.example.requests.domain.Request;
import com.example.requests.domain.RequestAction;
import com.example.requests.domain.RequestFilter;
import com.example.requests.domain.RequestLifecycle;
import com.example.requests.domain.RequestState;
import com.example.requests.infrastructure.web.RequestDtos.CreateRequestBody;
import com.example.requests.infrastructure.web.RequestDtos.PageResponse;
import com.example.requests.infrastructure.web.RequestDtos.ReasonBody;
import com.example.requests.infrastructure.web.RequestDtos.RequestResponse;
import com.example.requests.infrastructure.web.RequestDtos.StateChangeResponse;
import com.example.requests.infrastructure.web.RequestDtos.UpdateContentBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

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
	 * Creates a request in state CREATED.
	 *
	 * @return 201 with a {@code Location} header pointing to the new request
	 */
	@PostMapping
	ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequestBody body) {
		Request created = service.create(body.name(), body.content());
		return ResponseEntity
				.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
				.body(toResponse(created));
	}

	/**
	 * Returns a single request, or 404 if it does not exist.
	 */
	@GetMapping("/{id}")
	RequestResponse get(@PathVariable long id) {
		return toResponse(service.get(id));
	}

	/**
	 * Lists requests, optionally filtered.
	 *
	 * @param name  case-insensitive substring of the name
	 * @param state exact state
	 * @param page  zero-based page index
	 * @param size  page size; defaults to {@code app.pagination.default-size}, capped at {@code app.pagination.max-size}
	 */
	@GetMapping
	PageResponse<RequestResponse> search(
			@RequestParam(required = false) String name,
			@RequestParam(required = false) RequestState state,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(required = false) @Min(1) Integer size) {
		int pageSize = size == null ? pagination.defaultSize() : Math.min(size, pagination.maxSize());
		return PageResponse.from(service.search(new RequestFilter(name, state), new PageQuery(page, pageSize))
				.map(this::toResponse));
	}

	/**
	 * Replaces the content. Allowed only in CREATED and VERIFIED, otherwise 409.
	 */
	@PatchMapping("/{id}")
	RequestResponse editContent(@PathVariable long id, @Valid @RequestBody UpdateContentBody body) {
		return toResponse(service.editContent(id, body.content()));
	}

	/**
	 * CREATED → VERIFIED.
	 */
	@PostMapping("/{id}/verify")
	RequestResponse verify(@PathVariable long id) {
		return transition(id, RequestAction.VERIFY, null);
	}

	/**
	 * VERIFIED → ACCEPTED.
	 */
	@PostMapping("/{id}/accept")
	RequestResponse accept(@PathVariable long id) {
		return transition(id, RequestAction.ACCEPT, null);
	}

	/**
	 * ACCEPTED → PUBLISHED, assigning the publication number.
	 */
	@PostMapping("/{id}/publish")
	RequestResponse publish(@PathVariable long id) {
		return transition(id, RequestAction.PUBLISH, null);
	}

	/**
	 * VERIFIED or ACCEPTED → REJECTED. A reason is mandatory.
	 */
	@PostMapping("/{id}/reject")
	RequestResponse reject(@PathVariable long id, @Valid @RequestBody ReasonBody body) {
		return transition(id, RequestAction.REJECT, body.reason());
	}

	/**
	 * CREATED → DELETED. A reason is mandatory. This is a soft delete: the request remains queryable.
	 */
	@PostMapping("/{id}/delete")
	RequestResponse delete(@PathVariable long id, @Valid @RequestBody ReasonBody body) {
		return transition(id, RequestAction.DELETE, body.reason());
	}

	/**
	 * Returns the audit log of the request, oldest first.
	 */
	@GetMapping("/{id}/history")
	List<StateChangeResponse> history(@PathVariable long id) {
		return service.history(id).stream().map(StateChangeResponse::from).toList();
	}

	private RequestResponse transition(long id, RequestAction action, String reason) {
		return toResponse(service.transition(id, action, reason));
	}

	private RequestResponse toResponse(Request request) {
		return RequestResponse.from(request, lifecycle);
	}
}
