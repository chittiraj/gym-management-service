package com.gym.management.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT validation settings. The secret comes from JWT_SECRET and must match the Auth Service exactly.
 * This service only validates tokens; it never issues them.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String issuer, String secret) {
}
