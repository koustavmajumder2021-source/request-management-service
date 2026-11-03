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

	@PostMapping
	ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequestBody body) {
		Request created = service.create(body.name(), body.content());
		return ResponseEntity
				.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
				.body(toResponse(created));
	}

	@GetMapping("/{id}")
	RequestResponse get(@PathVariable long id) {
		return toResponse(service.get(id));
	}

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

	@PatchMapping("/{id}")
	RequestResponse editContent(@PathVariable long id, @Valid @RequestBody UpdateContentBody body) {
		return toResponse(service.editContent(id, body.content()));
	}

	@PostMapping("/{id}/verify")
	RequestResponse verify(@PathVariable long id) {
		return transition(id, RequestAction.VERIFY, null);
	}

	@PostMapping("/{id}/accept")
	RequestResponse accept(@PathVariable long id) {
		return transition(id, RequestAction.ACCEPT, null);
	}

	@PostMapping("/{id}/publish")
	RequestResponse publish(@PathVariable long id) {
		return transition(id, RequestAction.PUBLISH, null);
	}

	@PostMapping("/{id}/reject")
	RequestResponse reject(@PathVariable long id, @Valid @RequestBody ReasonBody body) {
		return transition(id, RequestAction.REJECT, body.reason());
	}

	/** Soft delete: the request stays queryable in state DELETED. */
	@PostMapping("/{id}/delete")
	RequestResponse delete(@PathVariable long id, @Valid @RequestBody ReasonBody body) {
		return transition(id, RequestAction.DELETE, body.reason());
	}

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
