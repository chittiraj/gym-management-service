package com.gym.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the supported member profile fields (PUT semantics). There is deliberately no role
 * field: a client cannot change a member's role through this request, and any role value sent is ignored.
 */
public record UpdateMemberRequest(
		@NotBlank @Size(max = 150) String fullName,
		@NotBlank @Email @Size(max = 150) String email,
		@Pattern(regexp = ValidationPatterns.PHONE_REGEX, message = ValidationPatterns.PHONE_MESSAGE) @Size(max = 20) String phone,
		@Size(max = 500) String address,
		@Size(max = 150) String emergencyContactName,
		@Pattern(regexp = ValidationPatterns.PHONE_REGEX, message = ValidationPatterns.PHONE_MESSAGE) @Size(max = 20) String emergencyContactPhone
) {
}
