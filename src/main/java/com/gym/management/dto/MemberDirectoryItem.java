package com.gym.management.dto;

import com.gym.management.service.MembershipStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row of the members directory. Trainer is not included: no table in the schema holds trainer assignments. */
public record MemberDirectoryItem(
		Long userId,
		String fullName,
		String email,
		String phone,
		boolean isActive,
		LocalDateTime createdAt,
		MembershipStatus membershipStatus,
		String currentPlanName,
		LocalDate validUntil,
		BigDecimal outstandingBalance
) {
}
