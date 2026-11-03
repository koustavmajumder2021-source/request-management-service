package com.example.requests.infrastructure.web;

import com.example.requests.domain.PageResult;
import com.example.requests.domain.Request;
import com.example.requests.domain.RequestAction;
import com.example.requests.domain.RequestLifecycle;
import com.example.requests.domain.RequestState;
import com.example.requests.domain.StateChange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

final class RequestDtos {

	private RequestDtos() {
	}

	record CreateRequestBody(
			@NotBlank @Size(max = Request.MAX_NAME_LENGTH) String name,
			@NotBlank @Size(max = Request.MAX_CONTENT_LENGTH) String content) {
	}

	record UpdateContentBody(@NotBlank @Size(max = Request.MAX_CONTENT_LENGTH) String content) {
	}

	record ReasonBody(@NotBlank @Size(max = Request.MAX_REASON_LENGTH) String reason) {
	}

	record RequestResponse(
			long id,
			String name,
			String content,
			RequestState state,
			Long publicationNumber,
			String reason,
			Set<RequestAction> allowedActions,
			boolean contentEditable,
			Instant createdAt,
			Instant updatedAt) {

		static RequestResponse from(Request r, RequestLifecycle lifecycle) {
			return new RequestResponse(r.id(), r.name(), r.content(), r.state(), r.publicationNumber(), r.reason(),
					lifecycle.allowedActions(r.state()), lifecycle.isContentEditable(r.state()),
					r.createdAt(), r.updatedAt());
		}
	}

	record StateChangeResponse(RequestAction action, RequestState fromState, RequestState toState, String reason,
			Instant occurredAt) {

		static StateChangeResponse from(StateChange c) {
			return new StateChangeResponse(c.action(), c.fromState(), c.toState(), c.reason(), c.occurredAt());
		}
	}

	record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

		static <T> PageResponse<T> from(PageResult<T> result) {
			return new PageResponse<>(result.items(), result.page(), result.size(), result.totalItems(),
					result.totalPages());
		}
	}
}
