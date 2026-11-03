package com.example.requests.infrastructure.web;

import com.example.requests.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebLayerUnitTest {

	@Test
	void optimisticLockConflictIs409() {
		var problem = new ApiExceptionHandler()
				.concurrentModification(new OptimisticLockingFailureException("stale"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(problem.getTitle()).isEqualTo("Concurrent modification");
	}

	@Test
	void domainValidationErrorIs400WithFieldDetail() {
		var problem = new ApiExceptionHandler()
				.domainValidation(new DomainValidationException("reason", "must not be blank"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(problem.getProperties()).containsEntry("errors", Map.of("reason", "reason must not be blank"));
	}

	@Test
	void paginationPropertiesAreValidated() {
		assertThat(new PaginationProperties(10, 100).defaultSize()).isEqualTo(10);
		assertThatThrownBy(() -> new PaginationProperties(0, 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PaginationProperties(50, 10)).isInstanceOf(IllegalArgumentException.class);
	}
}
