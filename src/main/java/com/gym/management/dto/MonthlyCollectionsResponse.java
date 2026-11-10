package com.gym.management.dto;

import java.math.BigDecimal;
import java.util.List;

/** Twelve months of successful collections for one calendar year. No growth targets: no table holds targets. */
public record MonthlyCollectionsResponse(
		int year,
		BigDecimal totalCollected,
		List<Month> months
) {

	public record Month(int month, String monthName, BigDecimal collectedAmount) {
	}

}
