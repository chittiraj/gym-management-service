package com.gym.management.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gym.management.config.JwtConfig;
import com.gym.management.config.SecurityConfig;
import com.gym.management.dto.MemberDetailResponse;
import com.gym.management.dto.MemberDirectoryItem;
import com.gym.management.dto.MemberResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.exception.ApiException;
import com.gym.management.service.MemberService;
import com.gym.management.service.MembershipStatus;
import com.gym.management.support.TestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = MemberController.class)
@Import({JwtConfig.class, SecurityConfig.class})
class MemberControllerWebTest {

	private static final String VALID_UPDATE = """
			{"fullName":"Test Member","email":"member@gmail.com","phone":"9876543210",
			 "address":"Hyderabad","emergencyContactName":"Raj","emergencyContactPhone":"9123456780"}
			""";

	private static final String VALID_CREATE = """
			{"fullName":"New Member","email":"new.member@gmail.com","password":"Member@123",
			 "phone":"9876543210","address":"Hyderabad","emergencyContactName":"Asha",
			 "emergencyContactPhone":"9123456780"}
			""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	MemberService memberService;

	@DynamicPropertySource
	static void jwtSecret(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TestSupport.JWT_SECRET);
	}

	private static final MemberResponse MEMBER = new MemberResponse(7L, "Test Member", "member@gmail.com",
			"9876543210", "Hyderabad", "Raj", "9123456780", true, LocalDateTime.now(), LocalDateTime.now());

	private static final MemberDirectoryItem DIRECTORY_ITEM = new MemberDirectoryItem(7L, "Test Member",
			"member@gmail.com", "9876543210", true, LocalDateTime.now(), MembershipStatus.ACTIVE, "Monthly",
			LocalDate.of(2026, 12, 31), new BigDecimal("1500.00"));

	private static final MemberDetailResponse DETAIL = new MemberDetailResponse(7L, "Test Member",
			"member@gmail.com", "9876543210", "Hyderabad", "Raj", "9123456780", true, LocalDateTime.now(),
			LocalDateTime.now(), MembershipStatus.ACTIVE, null, null);

	@BeforeEach
	void stubService() {
		when(memberService.findMembers(any(), any(), any(), anyInt(), anyInt(), any()))
				.thenReturn(new PageResponse<>(List.of(DIRECTORY_ITEM), 0, 20, 1, 1, true));
		when(memberService.getMember(anyLong())).thenReturn(DETAIL);
		when(memberService.updateMember(anyLong(), any())).thenReturn(MEMBER);
		when(memberService.updateStatus(anyLong(), anyBoolean())).thenReturn(MEMBER);
	}

	private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String role) {
		return request.header("Authorization", "Bearer " + TestSupport.validAccessToken(role));
	}

	// ---------- Missing, malformed and invalid tokens ----------

	@Test
	void listWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
	}

	@Test
	void expiredTokenIsUnauthorized() throws Exception {
		Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
		String expired = TestSupport.signed(TestSupport.key(), TestSupport.ISSUER, "7", "ADMIN",
				twoHoursAgo, Instant.now().minus(Duration.ofHours(1)), Map.of());

		mockMvc.perform(get("/api/members/7").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void passwordResetTokenIsNotAcceptedForMembers() throws Exception {
		String resetToken = TestSupport.signed(TestSupport.key(), TestSupport.ISSUER, "7", "ADMIN",
				Instant.now(), Instant.now().plus(Duration.ofMinutes(5)),
				Map.of("token_use", "password_reset", "otp_id", 99L));

		mockMvc.perform(get("/api/members").header("Authorization", "Bearer " + resetToken))
				.andExpect(status().isUnauthorized());
	}

	// ---------- Listing ----------

	@Test
	void memberRoleCannotListMembers() throws Exception {
		mockMvc.perform(withBearer(get("/api/members"), "MEMBER")).andExpect(status().isForbidden());
	}

	@Test
	void staffCanListMembersAndResponseHasNoCredentials() throws Exception {
		mockMvc.perform(withBearer(get("/api/members"), "STAFF"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].userId").value(7))
				.andExpect(jsonPath("$.content[0].isActive").value(true))
				.andExpect(jsonPath("$.content[0].membershipStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.content[0].outstandingBalance").value(1500.00))
				.andExpect(content().string(not(containsString("password"))))
				.andExpect(content().string(not(containsString("\"role\""))));
	}

	@Test
	void adminCanListMembers() throws Exception {
		mockMvc.perform(withBearer(get("/api/members"), "ADMIN")).andExpect(status().isOk());
	}

	@Test
	void membershipStatusFilterIsPassedToService() throws Exception {
		mockMvc.perform(withBearer(get("/api/members").param("status", "EXPIRING"), "ADMIN"))
				.andExpect(status().isOk());
		verify(memberService).findMembers(any(), any(), org.mockito.ArgumentMatchers.eq(MembershipStatus.EXPIRING),
				anyInt(), anyInt(), any());
	}

	@Test
	void unknownMembershipStatusIsBadRequest() throws Exception {
		mockMvc.perform(withBearer(get("/api/members").param("status", "PAUSED"), "ADMIN"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						org.hamcrest.Matchers.containsString("ACTIVE, EXPIRING or EXPIRED")));
		verify(memberService, never()).findMembers(any(), any(), any(), anyInt(), anyInt(), any());
	}

	@Test
	void oversizedPageIsRejected() throws Exception {
		mockMvc.perform(withBearer(get("/api/members").param("size", "500"), "ADMIN"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void negativePageIsRejected() throws Exception {
		mockMvc.perform(withBearer(get("/api/members").param("page", "-1"), "ADMIN"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void overlongSearchTermIsRejected() throws Exception {
		mockMvc.perform(withBearer(get("/api/members").param("search", "x".repeat(101)), "ADMIN"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unsupportedSortFromServiceIsBadRequest() throws Exception {
		when(memberService.findMembers(any(), any(), any(), anyInt(), anyInt(), any()))
				.thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Unsupported sort."));

		mockMvc.perform(withBearer(get("/api/members").param("sort", "password_hash"), "ADMIN"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Unsupported sort."));
	}

	// ---------- Member details ----------

	@Test
	void memberRoleCannotReadMemberDetails() throws Exception {
		mockMvc.perform(withBearer(get("/api/members/7"), "MEMBER")).andExpect(status().isForbidden());
	}

	@Test
	void staffCanReadMemberDetailsWithEmergencyContact() throws Exception {
		mockMvc.perform(withBearer(get("/api/members/7"), "STAFF"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emergencyContactName").value("Raj"))
				.andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
				.andExpect(content().string(not(containsString("password"))));
	}

	@Test
	void unknownOrNonMemberIdIsNotFound() throws Exception {
		when(memberService.getMember(anyLong()))
				.thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Member not found."));

		mockMvc.perform(withBearer(get("/api/members/99"), "ADMIN"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Member not found."));
	}

	@Test
	void nonNumericIdIsBadRequest() throws Exception {
		mockMvc.perform(withBearer(get("/api/members/abc"), "ADMIN")).andExpect(status().isBadRequest());
	}

	@Test
	void zeroIdIsBadRequest() throws Exception {
		mockMvc.perform(withBearer(get("/api/members/0"), "ADMIN")).andExpect(status().isBadRequest());
	}

	// ---------- Updates ----------

	@Test
	void memberRoleCannotUpdateMembers() throws Exception {
		mockMvc.perform(withBearer(put("/api/members/7"), "MEMBER")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
				.andExpect(status().isForbidden());
	}

	@Test
	void staffCanUpdateMemberProfile() throws Exception {
		mockMvc.perform(withBearer(put("/api/members/7"), "STAFF")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
				.andExpect(status().isOk());
	}

	@Test
	void roleInUpdateBodyIsIgnored() throws Exception {
		String withRole = VALID_UPDATE.replace("{", "{\"role\":\"ADMIN\",");

		mockMvc.perform(withBearer(put("/api/members/7"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content(withRole))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(7));
	}

	@Test
	void updateWithBlankNameIsValidationError() throws Exception {
		String blankName = VALID_UPDATE.replace("Test Member", " ");

		mockMvc.perform(withBearer(put("/api/members/7"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content(blankName))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.fullName").exists());
	}

	@Test
	void updateWithMalformedJsonIsBadRequest() throws Exception {
		mockMvc.perform(withBearer(put("/api/members/7"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void duplicateEmailFromServiceIsConflict() throws Exception {
		when(memberService.updateMember(anyLong(), any()))
				.thenThrow(new ApiException(HttpStatus.CONFLICT, "An account with this email already exists."));

		mockMvc.perform(withBearer(put("/api/members/7"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
				.andExpect(status().isConflict());
	}

	// ---------- Activation and deactivation: ADMIN only ----------

	@Test
	void staffCannotActivateOrDeactivateMembers() throws Exception {
		mockMvc.perform(withBearer(patch("/api/members/7/status"), "STAFF")
						.contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void memberCannotChangeStatus() throws Exception {
		mockMvc.perform(withBearer(patch("/api/members/7/status"), "MEMBER")
						.contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanDeactivateMember() throws Exception {
		mockMvc.perform(withBearer(patch("/api/members/7/status"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
				.andExpect(status().isOk());
	}

	@Test
	void statusBodyWithoutActiveIsValidationError() throws Exception {
		mockMvc.perform(withBearer(patch("/api/members/7/status"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.active").exists());
	}

	// ---------- Admin-created members ----------

	@Test
	void staffCannotCreateMember() throws Exception {
		mockMvc.perform(withBearer(post("/api/members"), "STAFF")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
				.andExpect(status().isForbidden());
		verify(memberService, never()).createMember(any());
	}

	@Test
	void memberCannotCreateMember() throws Exception {
		mockMvc.perform(withBearer(post("/api/members"), "MEMBER")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
				.andExpect(status().isForbidden());
		verify(memberService, never()).createMember(any());
	}

	@Test
	void createWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void adminCanCreateMemberAndResponseHasNoPasswordOrRole() throws Exception {
		when(memberService.createMember(any())).thenReturn(MEMBER);

		mockMvc.perform(withBearer(post("/api/members"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(7))
				.andExpect(content().string(not(containsString("password"))))
				.andExpect(content().string(not(containsString("\"role\""))));
		verify(memberService).createMember(any());
	}

	@Test
	void roleInCreateBodyIsIgnoredAndMemberIsStillCreated() throws Exception {
		when(memberService.createMember(any())).thenReturn(MEMBER);
		String withRole = VALID_CREATE.replace("{", "{\"role\":\"ADMIN\",");

		mockMvc.perform(withBearer(post("/api/members"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content(withRole))
				.andExpect(status().isCreated())
				.andExpect(content().string(not(containsString("\"role\""))));
	}

	@Test
	void weakPasswordIsValidationErrorAndNothingIsCreated() throws Exception {
		String weak = VALID_CREATE.replace("Member@123", "weak");

		mockMvc.perform(withBearer(post("/api/members"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content(weak))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.password").exists());
		verify(memberService, never()).createMember(any());
	}

	@Test
	void invalidEmailIsValidationError() throws Exception {
		String badEmail = VALID_CREATE.replace("new.member@gmail.com", "not-an-email");

		mockMvc.perform(withBearer(post("/api/members"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content(badEmail))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.email").exists());
		verify(memberService, never()).createMember(any());
	}

	@Test
	void duplicateEmailFromAccountServiceIsConflict() throws Exception {
		when(memberService.createMember(any()))
				.thenThrow(new ApiException(HttpStatus.CONFLICT, "An account with this email already exists."));

		mockMvc.perform(withBearer(post("/api/members"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
				.andExpect(status().isConflict());
	}

	@Test
	void unavailableAccountServiceIsServiceUnavailable() throws Exception {
		when(memberService.createMember(any()))
				.thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Account service is unavailable."));

		mockMvc.perform(withBearer(post("/api/members"), "ADMIN")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
				.andExpect(status().isServiceUnavailable());
	}

}
