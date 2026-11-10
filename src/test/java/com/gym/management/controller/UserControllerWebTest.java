package com.gym.management.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gym.management.config.JwtConfig;
import com.gym.management.config.SecurityConfig;
import com.gym.management.dto.PageResponse;
import com.gym.management.dto.UserResponse;
import com.gym.management.entity.Role;
import com.gym.management.service.UserService;
import com.gym.management.support.TestSupport;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = {UserController.class, HealthController.class})
@Import({JwtConfig.class, SecurityConfig.class})
class UserControllerWebTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	UserService userService;

	@DynamicPropertySource
	static void jwtSecret(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TestSupport.JWT_SECRET);
	}

	@BeforeEach
	void stubService() {
		UserResponse sample = new UserResponse(7L, "Test User", "member@gmail.com", "9876543210", "Hyderabad",
				Role.MEMBER, true, LocalDateTime.now(), LocalDateTime.now());
		when(userService.findActiveUsers(anyInt(), anyInt()))
				.thenReturn(new PageResponse<>(List.of(sample), 0, 20, 1, 1, true));
	}

	private static MockHttpServletRequestBuilder usersWithBearer(String token) {
		return get("/api/users").header("Authorization", "Bearer " + token);
	}

	// ---------- Public health endpoint ----------

	@Test
	void healthEndpointIsPublicWithoutJwt() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	// ---------- Missing, malformed and invalid tokens ----------

	@Test
	void usersWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
	}

	@Test
	void malformedTokenIsUnauthorized() throws Exception {
		mockMvc.perform(usersWithBearer("this.is.not-a-jwt")).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenWithInvalidSignatureIsUnauthorized() throws Exception {
		String forged = TestSupport.signed(
				JwtConfig.buildSecretKey("a-different-secret-that-is-long-enough-to-be-valid-xyz"),
				TestSupport.ISSUER, "7", "ADMIN", Instant.now(), Instant.now().plus(Duration.ofMinutes(30)), Map.of());

		mockMvc.perform(usersWithBearer(forged)).andExpect(status().isUnauthorized());
	}

	@Test
	void expiredTokenIsUnauthorized() throws Exception {
		Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
		String expired = TestSupport.signed(TestSupport.key(), TestSupport.ISSUER, "7", "ADMIN",
				twoHoursAgo, Instant.now().minus(Duration.ofHours(1)), Map.of());

		mockMvc.perform(usersWithBearer(expired)).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenFromIncorrectIssuerIsUnauthorized() throws Exception {
		String foreign = TestSupport.signed(TestSupport.key(), "some-other-service", "7", "ADMIN",
				Instant.now(), Instant.now().plus(Duration.ofMinutes(30)), Map.of());

		mockMvc.perform(usersWithBearer(foreign)).andExpect(status().isUnauthorized());
	}

	@Test
	void passwordResetTokenIsNotAcceptedAsAccessToken() throws Exception {
		String resetToken = TestSupport.signed(TestSupport.key(), TestSupport.ISSUER, "7", "ADMIN",
				Instant.now(), Instant.now().plus(Duration.ofMinutes(5)),
				Map.of("token_use", "password_reset", "otp_id", 99L));

		mockMvc.perform(usersWithBearer(resetToken)).andExpect(status().isUnauthorized());
	}

	// ---------- Role-based authorization ----------

	@Test
	void memberRoleIsForbidden() throws Exception {
		mockMvc.perform(usersWithBearer(TestSupport.validAccessToken("MEMBER")))
				.andExpect(status().isForbidden());
	}

	@Test
	void staffRoleIsAllowed() throws Exception {
		mockMvc.perform(usersWithBearer(TestSupport.validAccessToken("STAFF")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].userId").value(7))
				.andExpect(jsonPath("$.content[0].isActive").value(true));
	}

	@Test
	void adminRoleIsAllowed() throws Exception {
		mockMvc.perform(usersWithBearer(TestSupport.validAccessToken("ADMIN")))
				.andExpect(status().isOk());
	}

	// ---------- Data exposure and pagination ----------

	@Test
	void userResponseNeverContainsPasswordHashOrEmergencyContact() throws Exception {
		mockMvc.perform(usersWithBearer(TestSupport.validAccessToken("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("passwordHash"))))
				.andExpect(content().string(not(containsString("password_hash"))))
				.andExpect(content().string(not(containsString("emergencyContact"))));
	}

	@Test
	void oversizedPageIsRejected() throws Exception {
		mockMvc.perform(get("/api/users").param("size", "500")
						.header("Authorization", "Bearer " + TestSupport.validAccessToken("ADMIN")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void negativePageIsRejected() throws Exception {
		mockMvc.perform(get("/api/users").param("page", "-1")
						.header("Authorization", "Bearer " + TestSupport.validAccessToken("ADMIN")))
				.andExpect(status().isBadRequest());
	}

}
