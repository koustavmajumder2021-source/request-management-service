package com.example.requests.controller;

import com.example.requests.exception.ContentNotEditableException;
import com.example.requests.exception.DomainException;
import com.example.requests.exception.DomainValidationException;
import com.example.requests.exception.DuplicateRequestNameException;
import com.example.requests.exception.InvalidStateTransitionException;
import com.example.requests.exception.RequestNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps exceptions to RFC 9457 problem details. Framework errors are handled by the base class.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	/**
	 * 404 for an unknown request name.
	 */
	@ExceptionHandler(RequestNotFoundException.class)
	ProblemDetail notFound(RequestNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Request not found", ex.getMessage());
	}

	/**
	 * 409 when the name is already used by a request that is not deleted.
	 */
	@ExceptionHandler(DuplicateRequestNameException.class)
	ProblemDetail duplicateName(DuplicateRequestNameException ex) {
		return problem(HttpStatus.CONFLICT, "Duplicate name", ex.getMessage());
	}

	/**
	 * 409 for an action the state diagram does not allow; includes the current state and the action.
	 */
	@ExceptionHandler(InvalidStateTransitionException.class)
	ProblemDetail invalidTransition(InvalidStateTransitionException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "Invalid state transition", ex.getMessage());
		problem.setProperty("currentState", ex.currentState());
		problem.setProperty("action", ex.action());
		return problem;
	}

	/**
	 * 409 for a content edit outside CREATED and VERIFIED.
	 */
	@ExceptionHandler(ContentNotEditableException.class)
	ProblemDetail notEditable(ContentNotEditableException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "Content not editable", ex.getMessage());
		problem.setProperty("currentState", ex.currentState());
		return problem;
	}

	/**
	 * 400 for input rejected by the domain. Bean Validation normally catches invalid input first;
	 * this covers rules only the domain knows.
	 */
	@ExceptionHandler(DomainValidationException.class)
	ProblemDetail domainValidation(DomainValidationException ex) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage());
		problem.setProperty("errors", Map.of(ex.field(), ex.getMessage()));
		return problem;
	}

	/**
	 * 409 when another transaction changed the request first; the client can reload and retry.
	 */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail concurrentModification(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Concurrent modification",
				"The request was modified by another operation; reload it and retry");
	}

	/**
	 * 422 for any business rule violation without a dedicated handler. Spring always prefers the most
	 * specific handler, so this only applies to domain exceptions that are not mapped above.
	 */
	@ExceptionHandler(DomainException.class)
	ProblemDetail businessRuleViolation(DomainException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violation", ex.getMessage());
	}

	/**
	 * 400 with one message per invalid field of the request body.
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request body is invalid");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	/**
	 * 400 with one message per invalid request parameter.
	 */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getParameterValidationResults().forEach(result -> errors.putIfAbsent(
				result.getMethodParameter().getParameterName(),
				result.getResolvableErrors().get(0).getDefaultMessage()));
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request parameters are invalid");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
