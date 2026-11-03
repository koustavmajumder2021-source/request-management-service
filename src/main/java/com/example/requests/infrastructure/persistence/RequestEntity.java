package com.example.requests.infrastructure.persistence;

import com.example.requests.domain.RequestState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "requests")
class RequestEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, length = 10_000)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestState state;

	@Column(length = 1_000)
	private String reason;

	@Column(name = "publication_number", unique = true)
	private Long publicationNumber;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected RequestEntity() {
	}

	Long getId() {
		return id;
	}

	String getName() {
		return name;
	}

	void setName(String name) {
		this.name = name;
	}

	String getContent() {
		return content;
	}

	void setContent(String content) {
		this.content = content;
	}

	RequestState getState() {
		return state;
	}

	void setState(RequestState state) {
		this.state = state;
	}

	String getReason() {
		return reason;
	}

	void setReason(String reason) {
		this.reason = reason;
	}

	Long getPublicationNumber() {
		return publicationNumber;
	}

	void setPublicationNumber(Long publicationNumber) {
		this.publicationNumber = publicationNumber;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

	void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
