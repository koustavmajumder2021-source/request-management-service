package com.example.requests.repository.jpa;

import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_state_changes",
		indexes = @Index(name = "ix_request_state_changes_request_id", columnList = "request_id"))
class StateChangeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false)
	private UUID requestId;

	/** Read-only; only declares the foreign key to {@code requests}. Writes go through {@link #requestId}. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", insertable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_request_state_changes_request"))
	private RequestEntity request;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestAction action;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_state", length = 20)
	private RequestState fromState;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_state", nullable = false, length = 20)
	private RequestState toState;

	@Column(length = 1_000)
	private String reason;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected StateChangeEntity() {
	}

	StateChangeEntity(UUID requestId, RequestAction action, RequestState fromState, RequestState toState,
			String reason, Instant occurredAt) {
		this.requestId = requestId;
		this.action = action;
		this.fromState = fromState;
		this.toState = toState;
		this.reason = reason;
		this.occurredAt = occurredAt;
	}

	UUID getRequestId() {
		return requestId;
	}

	RequestAction getAction() {
		return action;
	}

	RequestState getFromState() {
		return fromState;
	}

	RequestState getToState() {
		return toState;
	}

	String getReason() {
		return reason;
	}

	Instant getOccurredAt() {
		return occurredAt;
	}
}
