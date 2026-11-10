package com.gym.management.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the Auth Service internal API. serviceKey comes from INTERNAL_SERVICE_KEY and must
 * match the value configured in gym-auth-service. An empty key disables member creation.
 */
@ConfigurationProperties(prefix = "app.auth-service")
public record AuthServiceProperties(String baseUrl, String serviceKey, Duration connectTimeout, Duration readTimeout) {
}
