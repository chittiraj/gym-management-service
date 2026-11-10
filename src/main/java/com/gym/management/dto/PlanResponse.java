package com.gym.management.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PlanResponse(
		Long planId,
		String planCode,
		String planName,
		String description,
		int durationMonths,
		BigDecimal price,
		boolean isActive,
		Integer displayOrder,
		List<Feature> features,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public record Feature(Long featureId, String featureName, String featureDescription, boolean included) {
	}

}
