package com.gym.management.support;

import com.gym.management.config.JwtConfig;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Test helpers. The JWT secret is random for each run and is never written into the repository. Tokens
 * are signed here to simulate tokens issued by gym-auth-service.
 */
public final class TestSupport {

	public static final String ISSUER = "gym-auth-service";
	public static final String JWT_SECRET = randomSecret();

	private TestSupport() {
	}

	private static String randomSecret() {
		byte[] bytes = new byte[48];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

	public static SecretKey key() {
		return JwtConfig.buildSecretKey(JWT_SECRET);
	}

	/** A well-formed, currently valid access token for the given role. */
	public static String validAccessToken(String role) {
		Instant now = Instant.now();
		return signed(key(), ISSUER, "7", role, now, now.plusSeconds(1800), Map.of());
	}

	public static String signed(SecretKey key, String issuer, String subject, String role, Instant issuedAt,
			Instant expiresAt, Map<String, Object> extraClaims) {
		JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(subject)
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("email", "member@gmail.com")
				.claim("role", role);
		extraClaims.forEach(builder::claim);
		return new NimbusJwtEncoder(new ImmutableSecret<>(key))
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build()))
				.getTokenValue();
	}

}
