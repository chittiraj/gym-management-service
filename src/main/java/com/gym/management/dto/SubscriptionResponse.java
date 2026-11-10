package com.gym.management.dto;

import com.gym.management.entity.PaymentOption;
import com.gym.management.entity.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A subscription summary. status is the stored value. effectiveStatus is derived from the dates today:
 * CANCELLED, PENDING (not started), ACTIVE (covers today) or EXPIRED (ended). Plan fields are the snapshots.
 */
public record SubscriptionResponse(
		Long subscriptionId,
		Long userId,
		String memberName,
		Long planId,
		String planCode,
		String planName,
		int durationMonths,
		BigDecimal totalAmount,
		PaymentOption paymentOption,
		LocalDate startDate,
		LocalDate endDate,
		SubscriptionStatus status,
		SubscriptionStatus effectiveStatus,
		BigDecimal amountPaid,
		BigDecimal outstandingBalance
) {

	/** A subscription with its installment schedule. */
	public record Detail(SubscriptionResponse subscription, List<InstallmentResponse> installments) {
	}

}
