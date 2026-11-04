package com.example.requests.domain;

import com.example.requests.domain.exception.ContentNotEditableException;
import com.example.requests.domain.exception.DomainValidationException;
import com.example.requests.domain.exception.InvalidStateTransitionException;
import com.example.requests.domain.port.PublicationNumberGenerator;

import java.time.Instant;
import java.util.Objects;

/**
 * Request aggregate. Owns all lifecycle invariants; has no framework dependencies.
 */
public final class Request {

	public static final int MAX_NAME_LENGTH = 255;
	public static final int MAX_CONTENT_LENGTH = 10_000;
	public static final int MAX_REASON_LENGTH = 1_000;

	private final Long id;
	private final String name;
	private String content;
	private RequestState state;
	private Long publicationNumber;
	private String reason;
	private final Instant createdAt;
	private Instant updatedAt;

	private Request(Long id, String name, String content, RequestState state, Long publicationNumber,
			String reason, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.name = name;
		this.content = content;
		this.state = state;
		this.publicationNumber = publicationNumber;
		this.reason = reason;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Creates a new, not yet persisted request in the lifecycle's initial state.
	 * Name and content are stored exactly as given.
	 *
	 * @throws DomainValidationException if the name or content is blank or exceeds its maximum length
	 */
	public static Request create(String name, String content, RequestLifecycle lifecycle, Instant now) {
		return new Request(null, requireText(name, "name", MAX_NAME_LENGTH),
				requireText(content, "content", MAX_CONTENT_LENGTH), lifecycle.initialState(), null, null, now, now);
	}

	/** Rehydrates a persisted request; invariants are not re-validated. */
	public static Request restore(long id, String name, String content, RequestState state, Long publicationNumber,
			String reason, Instant createdAt, Instant updatedAt) {
		return new Request(id, name, content, state, publicationNumber, reason, createdAt, updatedAt);
	}

	/**
	 * Replaces the content. This is not a state change.
	 *
	 * @throws ContentNotEditableException if the lifecycle does not allow editing in the current state
	 * @throws DomainValidationException   if the new content is blank or too long
	 */
	public void editContent(String newContent, RequestLifecycle lifecycle, Instant now) {
		if (!lifecycle.isContentEditable(state)) {
			throw new ContentNotEditableException(state);
		}
		this.content = requireText(newContent, "content", MAX_CONTENT_LENGTH);
		this.updatedAt = now;
	}

	/**
	 * Applies a lifecycle action to this request.
	 * <p>
	 * The transition is validated first, then the reason. Only when both are valid is the state changed and,
	 * on publication, a number drawn from {@code publicationNumbers}. A refused action therefore leaves the
	 * request untouched and never consumes a publication number.
	 *
	 * @param reason mandatory for actions that require one, optional otherwise; stored trimmed
	 * @return the audit entry describing the change
	 * @throws InvalidStateTransitionException if the action is not allowed in the current state
	 * @throws DomainValidationException       if a required reason is missing or a reason is too long
	 */
	public StateChange apply(RequestAction action, String reason, RequestLifecycle lifecycle,
			PublicationNumberGenerator publicationNumbers, Instant now) {
		RequestState from = state;
		RequestState to = lifecycle.next(from, action);
		String normalizedReason = normalizeReason(action, reason);
		if (to == RequestState.PUBLISHED) {
			this.publicationNumber = publicationNumbers.next();
		}
		this.state = to;
		this.reason = normalizedReason;
		this.updatedAt = now;
		return new StateChange(id, action, from, to, normalizedReason, now);
	}

	private static String normalizeReason(RequestAction action, String reason) {
		boolean blank = reason == null || reason.isBlank();
		if (blank) {
			if (action.isReasonRequired()) {
				throw new DomainValidationException("reason", "is required to " + action.name().toLowerCase());
			}
			return null;
		}
		return requireText(reason, "reason", MAX_REASON_LENGTH).strip();
	}

	private static String requireText(String value, String field, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new DomainValidationException(field, "must not be blank");
		}
		if (value.length() > maxLength) {
			throw new DomainValidationException(field, "must be at most " + maxLength + " characters");
		}
		return value;
	}

	public Long id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String content() {
		return content;
	}

	public RequestState state() {
		return state;
	}

	/**
	 * @return the publication number, or {@code null} until the request is published
	 */
	public Long publicationNumber() {
		return publicationNumber;
	}

	/** Reason supplied with the transition into the current state, if any. */
	public String reason() {
		return reason;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof Request other && id != null && id.equals(other.id));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
