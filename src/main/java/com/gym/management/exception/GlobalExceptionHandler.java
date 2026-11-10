package com.gym.management.exception;

import com.gym.management.dto.ApiErrorResponse;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Uniform error bodies. Never echoes tokens, request payloads or database details. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException ex) {
		return build(HttpStatus.BAD_REQUEST, "Invalid request parameters", null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
		if (ex instanceof ErrorResponse errorResponse) {
			HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
			if (status != null) {
				return build(status, status.getReasonPhrase(), null);
			}
		}
		log.error("Unexpected error while processing request", ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
	}

	private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message,
			Map<String, String> fieldErrors) {
		ApiErrorResponse body = new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
				message, fieldErrors);
		return ResponseEntity.status(status).body(body);
	}

}
