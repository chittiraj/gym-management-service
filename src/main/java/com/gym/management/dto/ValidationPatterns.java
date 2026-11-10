package com.gym.management.dto;

/** Validation rules for request DTOs. Mirrors the Auth Service rules so both services accept the same input. */
public final class ValidationPatterns {

	/**
	 * 8-72 printable ASCII characters (no spaces) with at least one lower-case letter, one upper-case
	 * letter, one digit and one special character. Same rule as gym-auth-service.
	 */
	public static final String PASSWORD_REGEX =
			"^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d])[\\x21-\\x7E]{8,72}$";
	public static final String PASSWORD_MESSAGE =
			"Password must be 8-72 characters without spaces and include an upper-case letter, a lower-case letter, a digit and a special character";

	/** Optional phone number: blank, or 7-20 characters of digits, spaces, brackets, dashes and a leading plus. */
	public static final String PHONE_REGEX = "^$|^\\+?[0-9 ()-]{7,20}$";
	public static final String PHONE_MESSAGE = "Invalid phone number";

	private ValidationPatterns() {
	}

}
