package com.gym.management.dto;

import java.math.BigDecimal;

/**
 * Dashboard figures, all computed from the database.
 *
 * @param totalMembers        MEMBER accounts, active or not
 * @param activeMembers       active member accounts with a live subscription that covers today (includes expiring)
 * @param expiringMemberships active member accounts whose live subscription ends within the next 7 days
 * @param monthlyRevenue      successful payments dated in the current calendar month
 * @param totalCollections    successful payments to date
 */
public record DashboardSummaryResponse(
		long totalMembers,
		long activeMembers,
		long expiringMemberships,
		BigDecimal monthlyRevenue,
		BigDecimal totalCollections
) {
}
