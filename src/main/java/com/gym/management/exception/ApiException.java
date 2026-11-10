package com.gym.management.exception;

import org.springframework.http.HttpStatus;

/** Expected business failure with a safe, client-facing message. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	public ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

}
