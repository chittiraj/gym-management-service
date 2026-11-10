package com.gym.management.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import java.util.Set;

/**
 * Access-token rules. These must match the Auth Service exactly. Password-reset tokens carry the
 * {@value #TOKEN_USE} claim and are rejected here, so they cannot access this service.
 */
public final class GymJwtValidators {

	public static final String TOKEN_USE = "token_use";
	public static final String ROLE = "role";
	public static final Set<String> ROLES = Set.of("ADMIN", "STAFF", "MEMBER");

	private GymJwtValidators() {
	}

	/** Signature/expiry/issuer (Spring defaults), numeric subject, known role, and not a reset token. */
	public static OAuth2TokenValidator<Jwt> accessToken(String issuer) {
		return new DelegatingOAuth2TokenValidator<Jwt>(
				JwtValidators.createDefaultWithIssuer(issuer),
				GymJwtValidators::validSubject,
				GymJwtValidators::validRole,
				GymJwtValidators::notResetToken);
	}

	private static OAuth2TokenValidatorResult validSubject(Jwt jwt) {
		String subject = jwt.getSubject();
		if (subject != null && subject.matches("\\d{1,19}")) {
			return OAuth2TokenValidatorResult.success();
		}
		return failure("Token subject must be a numeric user id");
	}

	private static OAuth2TokenValidatorResult validRole(Jwt jwt) {
		String role = jwt.getClaimAsString(ROLE);
		if (role != null && ROLES.contains(role)) {
			return OAuth2TokenValidatorResult.success();
		}
		return failure("Token role claim is missing or unknown");
	}

	private static OAuth2TokenValidatorResult notResetToken(Jwt jwt) {
		if (jwt.hasClaim(TOKEN_USE)) {
			return failure("Token type is not accepted for this operation");
		}
		return OAuth2TokenValidatorResult.success();
	}

	private static OAuth2TokenValidatorResult failure(String description) {
		return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
	}

}
