package com.gym.management.dto;

import com.gym.management.entity.PaymentOption;
import com.gym.management.entity.SubscriptionStatus;
import com.gym.management.service.MembershipStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Full member profile. Personal fields match MemberResponse. Trainer, body metrics, fitness goals and locker
 * allocation are not included because the schema has no tables for them. password_hash is never included.
 */
public record MemberDetailResponse(
		Long userId,
		String fullName,
		String email,
		String phone,
		String address,
		String emergencyContactName,
		String emergencyContactPhone,
		boolean isActive,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		MembershipStatus membershipStatus,
		CurrentSubscriptionSummary currentSubscription,
		PaymentSummary paymentSummary
) {

	/** The subscription shown on the profile. Plan fields are the snapshots taken when it was assigned. */
	public record CurrentSubscriptionSummary(
			Long subscriptionId,
			String planCode,
			String planName,
			LocalDate startDate,
			LocalDate validUntil,
			BigDecimal subscriptionAmount,
			PaymentOption paymentOption,
			SubscriptionStatus status
	) {
	}

	/**
	 * outstandingBalance covers all non-cancelled subscriptions. The other figures refer to the current
	 * subscription. paymentProgressPercent is null when the subscription has no amount.
	 */
	public record PaymentSummary(
			BigDecimal amountReceived,
			BigDecimal outstandingBalance,
			int totalInstallments,
			BigDecimal monthlyDue,
			BigDecimal paymentProgressPercent
	) {
	}

}
