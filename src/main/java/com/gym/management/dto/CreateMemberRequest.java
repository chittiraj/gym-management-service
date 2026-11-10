package com.gym.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Details for a new member account created by an ADMIN. There is deliberately no role field: the Auth
 * Service always creates MEMBER accounts, and a role sent by the client is ignored.
 */
public record CreateMemberRequest(
		@NotBlank @Size(max = 150) String fullName,
		@NotBlank @Email @Size(max = 150) String email,
		@NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD_REGEX, message = ValidationPatterns.PASSWORD_MESSAGE) String password,
		@Pattern(regexp = ValidationPatterns.PHONE_REGEX, message = ValidationPatterns.PHONE_MESSAGE) @Size(max = 20) String phone,
		@Size(max = 500) String address,
		@Size(max = 150) String emergencyContactName,
		@Pattern(regexp = ValidationPatterns.PHONE_REGEX, message = ValidationPatterns.PHONE_MESSAGE) @Size(max = 20) String emergencyContactPhone
) {

	/** The default record toString would print the password. Logging this object must never expose it. */
	@Override
	public String toString() {
		return "CreateMemberRequest[redacted]";
	}

}
