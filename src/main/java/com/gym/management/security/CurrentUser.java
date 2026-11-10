package com.gym.management.security;

import com.gym.management.entity.Role;
import com.gym.management.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller, read from the JWT that was already verified (signature, expiry, issuer, role). The
 * request body is never used to identify the caller.
 */
public record CurrentUser(long userId, Role role) {

	/** Builds the caller from a verified token. Throws 401 if the subject or role cannot be read. */
	public static CurrentUser from(Jwt jwt) {
		if (jwt == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
		}
		try {
			long userId = Long.parseLong(jwt.getSubject());
			Role role = Role.valueOf(jwt.getClaimAsString(GymJwtValidators.ROLE));
			return new CurrentUser(userId, role);
		} catch (RuntimeException ex) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is no longer valid.");
		}
	}

	/** ADMIN and STAFF may read any member. A MEMBER may read only their own record. */
	public void requireCanViewMember(long memberId) {
		if (role == Role.MEMBER && userId != memberId) {
			throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own records.");
		}
	}

}
