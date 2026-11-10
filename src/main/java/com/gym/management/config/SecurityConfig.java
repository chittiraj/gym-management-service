package com.gym.management.config;

import com.gym.management.security.GymJwtValidators;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authorization rules. Rules are matched in order and the first match wins, so specific paths come before the
 * catch-all rules. Any method not listed for a resource falls to an ADMIN-only catch-all, so MEMBER callers never
 * reach administrative resources through an unexpected verb.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String ADMIN = "ADMIN";
	private static final String STAFF = "STAFF";
	private static final String MEMBER = "MEMBER";

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// Stateless bearer-token API: no cookies or sessions, so CSRF protection does not apply.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
						.requestMatchers("/error").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/users").hasAnyRole(ADMIN, STAFF)

						// Members. Member-scoped reads below let a MEMBER read only their own record; the service enforces that.
						.requestMatchers(HttpMethod.GET, "/api/members", "/api/members/*").hasAnyRole(ADMIN, STAFF)
						.requestMatchers(HttpMethod.GET, "/api/members/*/subscription", "/api/members/*/payments",
								"/api/members/*/installments").hasAnyRole(ADMIN, STAFF, MEMBER)
						.requestMatchers(HttpMethod.PUT, "/api/members/*").hasAnyRole(ADMIN, STAFF)
						.requestMatchers(HttpMethod.PATCH, "/api/members/*/status").hasRole(ADMIN)
						.requestMatchers("/api/members", "/api/members/**").hasRole(ADMIN)

						// Dashboard: read-only, ADMIN and STAFF.
						.requestMatchers("/api/dashboard/**").hasAnyRole(ADMIN, STAFF)

						// Plans: read by ADMIN and STAFF; any write is ADMIN only.
						.requestMatchers(HttpMethod.GET, "/api/plans", "/api/plans/*").hasAnyRole(ADMIN, STAFF)
						.requestMatchers("/api/plans", "/api/plans/**").hasRole(ADMIN)

						// Subscriptions: read by ADMIN and STAFF; assigning and renewing are ADMIN only.
						.requestMatchers(HttpMethod.GET, "/api/subscriptions", "/api/subscriptions/**")
						.hasAnyRole(ADMIN, STAFF)
						.requestMatchers("/api/subscriptions", "/api/subscriptions/**").hasRole(ADMIN)

						// Payments and installments: ADMIN and STAFF record and read.
						.requestMatchers("/api/payments", "/api/payments/**").hasAnyRole(ADMIN, STAFF)
						.requestMatchers("/api/installments", "/api/installments/**").hasAnyRole(ADMIN, STAFF)

						// Media: ADMIN and STAFF read, create and edit; deleting (a soft delete) is ADMIN only.
						.requestMatchers(HttpMethod.DELETE, "/api/media/*").hasRole(ADMIN)
						.requestMatchers(HttpMethod.GET, "/api/media", "/api/media/*").hasAnyRole(ADMIN, STAFF)
						.requestMatchers(HttpMethod.POST, "/api/media").hasAnyRole(ADMIN, STAFF)
						.requestMatchers(HttpMethod.PUT, "/api/media/*").hasAnyRole(ADMIN, STAFF)
						.requestMatchers("/api/media", "/api/media/**").hasRole(ADMIN)

						.requestMatchers("/api/**").authenticated()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())));
		return http.build();
	}

	/** Maps the validated role claim to a ROLE_ authority, e.g. STAFF -> ROLE_STAFF. */
	static JwtAuthenticationConverter roleConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> List.<GrantedAuthority>of(
				new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(GymJwtValidators.ROLE))));
		return converter;
	}

}
