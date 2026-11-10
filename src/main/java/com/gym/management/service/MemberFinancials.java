package com.gym.management.service;

import com.gym.management.entity.MemberSubscription;
import java.math.BigDecimal;

/**
 * Membership and money figures for one member, built from one batch of subscriptions and installments.
 *
 * @param membershipStatus    derived from subscription dates (see MembershipCalculator)
 * @param currentSubscription the valid subscription if one exists, otherwise the most recent non-cancelled one; null if none
 * @param outstandingBalance  money still owed across all non-cancelled subscriptions
 * @param currentAmount       total_amount of the current subscription
 * @param amountReceived      paid so far on the current subscription
 * @param installmentCount    installments on the current subscription
 * @param monthlyDue          due amount of the next unpaid installment on the current subscription, or null
 */
public record MemberFinancials(
		MembershipStatus membershipStatus,
		MemberSubscription currentSubscription,
		BigDecimal outstandingBalance,
		BigDecimal currentAmount,
		BigDecimal amountReceived,
		int installmentCount,
		BigDecimal monthlyDue
) {

	/** Snapshot for a member with no subscriptions. */
	public static MemberFinancials none() {
		BigDecimal zero = BigDecimal.ZERO.setScale(2);
		return new MemberFinancials(MembershipStatus.NONE, null, zero, zero, zero, 0, null);
	}

}
