package com.gym.management.controller;

import com.gym.management.dto.DashboardSummaryResponse;
import com.gym.management.dto.MonthlyCollectionsResponse;
import com.gym.management.service.DashboardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN and STAFF only, enforced in SecurityConfig. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/summary")
	public DashboardSummaryResponse summary() {
		return dashboardService.summary();
	}

	@GetMapping("/monthly-collections")
	public MonthlyCollectionsResponse monthlyCollections(
			@RequestParam(name = "year") @Min(2000) @Max(2100) int year) {
		return dashboardService.monthlyCollections(year);
	}

}
