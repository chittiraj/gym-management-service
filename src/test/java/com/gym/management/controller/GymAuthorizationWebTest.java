package com.gym.management.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gym.management.config.JwtConfig;
import com.gym.management.config.SecurityConfig;
import com.gym.management.service.DashboardService;
import com.gym.management.service.InstallmentService;
import com.gym.management.service.MediaService;
import com.gym.management.service.PaymentService;
import com.gym.management.service.PlanService;
import com.gym.management.service.SubscriptionService;
import com.gym.management.support.TestSupport;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Role matrix for every new endpoint. Each row states whether a role may reach the endpoint. Allowed rows assert
 * only that security did not refuse the call (the service is mocked, so the response body is not checked here).
 * Denied rows must return 403 for an authenticated caller, and 401 without a valid token.
 */
@WebMvcTest(controllers = {PlanController.class, SubscriptionController.class, PaymentController.class,
		InstallmentController.class, MemberInstallmentController.class, MediaController.class,
		DashboardController.class})
@Import({JwtConfig.class, SecurityConfig.class})
class GymAuthorizationWebTest {

	private static final String BODY = "{}";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PlanService planService;
	@MockitoBean
	SubscriptionService subscriptionService;
	@MockitoBean
	PaymentService paymentService;
	@MockitoBean
	InstallmentService installmentService;
	@MockitoBean
	MediaService mediaService;
	@MockitoBean
	DashboardService dashboardService;

	@DynamicPropertySource
	static void jwtSecret(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TestSupport.JWT_SECRET);
	}

	static Stream<Arguments> matrix() {
		return Stream.of(
				// Plans: reads for ADMIN and STAFF, writes for ADMIN only.
				row("GET", "/api/plans", "ADMIN", true),
				row("GET", "/api/plans", "STAFF", true),
				row("GET", "/api/plans", "MEMBER", false),
				row("GET", "/api/plans/1", "STAFF", true),
				row("POST", "/api/plans", "ADMIN", true),
				row("POST", "/api/plans", "STAFF", false),
				row("PUT", "/api/plans/1", "STAFF", false),
				row("PUT", "/api/plans/1", "ADMIN", true),
				row("PATCH", "/api/plans/1/status", "STAFF", false),
				row("PATCH", "/api/plans/1/status", "ADMIN", true),

				// Subscriptions: reads for ADMIN and STAFF, assignment and renewal for ADMIN only.
				row("GET", "/api/subscriptions", "STAFF", true),
				row("GET", "/api/subscriptions", "MEMBER", false),
				row("GET", "/api/subscriptions/1", "STAFF", true),
				row("GET", "/api/subscriptions/1", "MEMBER", false),
				row("GET", "/api/subscriptions/expiring", "STAFF", true),
				row("GET", "/api/subscriptions/expiring", "MEMBER", false),
				row("GET", "/api/subscriptions/expired", "STAFF", true),
				row("POST", "/api/subscriptions", "STAFF", false),
				row("POST", "/api/subscriptions", "ADMIN", true),
				row("POST", "/api/subscriptions/1/renew", "STAFF", false),
				row("POST", "/api/subscriptions/1/renew", "ADMIN", true),
				// A MEMBER reaches their own subscription through the member route; the service checks ownership.
				row("GET", "/api/members/1/subscription", "MEMBER", true),
				row("GET", "/api/members/1/subscription", "STAFF", true),

				// Payments: ADMIN and STAFF record and read; a MEMBER reads only their own history.
				row("GET", "/api/payments", "STAFF", true),
				row("GET", "/api/payments", "MEMBER", false),
				row("POST", "/api/payments", "STAFF", true),
				row("POST", "/api/payments", "MEMBER", false),
				row("GET", "/api/payments/1", "MEMBER", false),
				row("GET", "/api/payments/1/invoice", "STAFF", true),
				row("GET", "/api/payments/1/invoice", "MEMBER", false),
				row("GET", "/api/members/1/payments", "MEMBER", true),

				// Installments.
				row("GET", "/api/installments", "STAFF", true),
				row("GET", "/api/installments", "MEMBER", false),
				row("GET", "/api/installments/pending", "STAFF", true),
				row("GET", "/api/installments/pending", "MEMBER", false),
				row("GET", "/api/installments/1", "STAFF", true),
				row("POST", "/api/installments/1/payments", "STAFF", true),
				row("POST", "/api/installments/1/payments", "MEMBER", false),
				row("GET", "/api/members/1/installments", "MEMBER", true),

				// Media: ADMIN and STAFF manage; deletion is ADMIN only.
				row("GET", "/api/media", "STAFF", true),
				row("GET", "/api/media", "MEMBER", false),
				row("POST", "/api/media", "STAFF", true),
				row("POST", "/api/media", "MEMBER", false),
				row("GET", "/api/media/1", "STAFF", true),
				row("PUT", "/api/media/1", "STAFF", true),
				row("PUT", "/api/media/1", "MEMBER", false),
				row("DELETE", "/api/media/1", "STAFF", false),
				row("DELETE", "/api/media/1", "ADMIN", true),
				row("DELETE", "/api/media/1", "MEMBER", false),

				// Dashboard.
				row("GET", "/api/dashboard/summary", "STAFF", true),
				row("GET", "/api/dashboard/summary", "MEMBER", false),
				row("GET", "/api/dashboard/monthly-collections", "MEMBER", false),
				row("GET", "/api/dashboard/monthly-collections", "ADMIN", true)
		);
	}

	private static Arguments row(String method, String path, String role, boolean allowed) {
		return Arguments.of(method, path, role, allowed);
	}

	@ParameterizedTest(name = "{0} {1} as {2} allowed={3}")
	@MethodSource("matrix")
	void roleMatrixIsEnforced(String method, String path, String role, boolean allowed) throws Exception {
		MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path)
				.header("Authorization", "Bearer " + TestSupport.validAccessToken(role));
		if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
			builder.contentType(MediaType.APPLICATION_JSON).content(BODY);
		}
		if (path.endsWith("/monthly-collections")) {
			builder.param("year", "2026");
		}

		var result = mockMvc.perform(builder).andReturn();
		int code = result.getResponse().getStatus();
		if (allowed) {
			org.assertj.core.api.Assertions.assertThat(code).isNotIn(401, 403);
		} else {
			org.assertj.core.api.Assertions.assertThat(code).isEqualTo(403);
		}
	}

	@ParameterizedTest(name = "{0} {1} without a token is 401")
	@MethodSource("unauthenticatedRows")
	void missingTokenIsUnauthorized(String method, String path) throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(method), path))
				.andExpect(status().isUnauthorized());
	}

	static Stream<Arguments> unauthenticatedRows() {
		return Stream.of(
				Arguments.of("GET", "/api/plans"),
				Arguments.of("GET", "/api/subscriptions"),
				Arguments.of("GET", "/api/payments"),
				Arguments.of("GET", "/api/installments"),
				Arguments.of("GET", "/api/media"),
				Arguments.of("GET", "/api/dashboard/summary"));
	}

	@ParameterizedTest(name = "{0} {1} with a garbage token is 401")
	@MethodSource("unauthenticatedRows")
	void malformedTokenIsUnauthorized(String method, String path) throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer not.a.jwt"))
				.andExpect(status().isUnauthorized());
	}

}
