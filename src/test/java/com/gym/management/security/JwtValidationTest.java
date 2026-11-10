package com.gym.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gym.management.config.JwtConfig;
import com.gym.management.support.TestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** Direct validation of the access-token rules shared with gym-auth-service. */
class JwtValidationTest {

	private final JwtDecoder decoder = JwtConfig.buildAccessTokenDecoder(TestSupport.key(), TestSupport.ISSUER);

	@Test
	void validTokenIsAcceptedWithSubjectAndRole() {
		Jwt jwt = decoder.decode(TestSupport.validAccessToken("STAFF"));

		assertThat(jwt.getSubject()).isEqualTo("7");
		assertThat(jwt.getClaimAsString("role")).isEqualTo("STAFF");
	}

	@Test
	void tokenWithUnknownRoleIsRejected() {
		String token = signed("SUPERUSER", Map.of());

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void tokenWithNonNumericSubjectIsRejected() {
		String token = TestSupport.signed(TestSupport.key(), TestSupport.ISSUER, "abc", "ADMIN",
				Instant.now(), Instant.now().plus(Duration.ofMinutes(30)), Map.of());

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void tokenWithTokenUseClaimIsRejected() {
		String token = signed("ADMIN", Map.of("token_use", "password_reset"));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void expiredTokenIsRejected() {
		String token = TestSupport.signed(TestSupport.key(), TestSupport.ISSUER, "7", "ADMIN",
				Instant.now().minus(Duration.ofHours(2)), Instant.now().minus(Duration.ofHours(1)), Map.of());

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void secretShorterThan256BitsIsRejectedWithoutEchoingIt() {
		assertThatThrownBy(() -> JwtConfig.buildSecretKey("too-short"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("too-short");
	}

	private static String signed(String role, Map<String, Object> extra) {
		Instant now = Instant.now();
		return TestSupport.signed(TestSupport.key(), TestSupport.ISSUER, "7", role, now,
				now.plus(Duration.ofMinutes(30)), extra);
	}

}
