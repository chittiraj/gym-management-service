package com.gym.management.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gym.management.config.JwtConfig;
import com.gym.management.config.SecurityConfig;
import com.gym.management.dto.DashboardSummaryResponse;
import com.gym.management.dto.MonthlyCollectionsResponse;
import com.gym.management.service.DashboardService;
import com.gym.management.support.TestSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import({JwtConfig.class, SecurityConfig.class})
class DashboardControllerWebTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	DashboardService dashboardService;

	@DynamicPropertySource
	static void jwtSecret(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TestSupport.JWT_SECRET);
	}

	@BeforeEach
	void stub() {
		when(dashboardService.summary()).thenReturn(new DashboardSummaryResponse(40, 25, 4,
				new BigDecimal("45280.00"), new BigDecimal("384000.00")));
		when(dashboardService.monthlyCollections(2026)).thenReturn(
				new MonthlyCollectionsResponse(2026, new BigDecimal("1200.00"), List.of(
						new MonthlyCollectionsResponse.Month(1, "January", new BigDecimal("1200.00")))));
	}

	@Test
	void staffReadsSummaryWithAllFourFigures() throws Exception {
		mockMvc.perform(get("/api/dashboard/summary")
						.header("Authorization", "Bearer " + TestSupport.validAccessToken("STAFF")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activeMembers").value(25))
				.andExpect(jsonPath("$.expiringMemberships").value(4))
				.andExpect(jsonPath("$.monthlyRevenue").value(45280.0))
				.andExpect(jsonPath("$.totalCollections").value(384000.0));
	}

	@Test
	void staffReadsMonthlyCollectionsForTheYear() throws Exception {
		mockMvc.perform(get("/api/dashboard/monthly-collections").param("year", "2026")
						.header("Authorization", "Bearer " + TestSupport.validAccessToken("STAFF")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.months[0].monthName").value("January"));
	}

}
