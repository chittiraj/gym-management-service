package com.gym.management.service;

import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.SubscriptionStatus;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure membership rules. The SQL filters in SubscriptionSpecifications implement the same definitions, and the
 * unit tests check both against the same cases.
 *
 * <ul>
 *   <li><b>Valid</b>: status ACTIVE, start_date &lt;= today &lt;= end_date. CANCELLED and PENDING never count.</li>
 *   <li><b>ACTIVE</b>: a valid subscription ends more than {@value #EXPIRING_WINDOW_DAYS} days from today.</li>
 *   <li><b>EXPIRING</b>: a valid subscription ends within the next {@value #EXPIRING_WINDOW_DAYS} days, today included.</li>
 *   <li><b>PENDING</b>: no valid subscription, but a PENDING or ACTIVE subscription starts after today.</li>
 *   <li><b>EXPIRED</b>: none of the above, and an ACTIVE or EXPIRED subscription ended before today.</li>
 *   <li><b>NONE</b>: anything else, including members with no subscriptions.</li>
 * </ul>
 */
public final class MembershipCalculator {

	public static final int EXPIRING_WINDOW_DAYS = 7;

	private MembershipCalculator() {
	}

	/**
	 * Covers today. The stored status is not refreshed when a start date passes, so PENDING and ACTIVE are both
	 * live and the dates decide. Only CANCELLED and EXPIRED rows are excluded here.
	 */
	public static boolean isValid(MemberSubscription subscription, LocalDate today) {
		return isLive(subscription)
				&& !subscription.getStartDate().isAfter(today)
				&& !subscription.getEndDate().isBefore(today);
	}

	static boolean isLive(MemberSubscription subscription) {
		return subscription.getStatus() == SubscriptionStatus.ACTIVE
				|| subscription.getStatus() == SubscriptionStatus.PENDING;
	}

	/** The valid subscription that ends latest. Only one should exist, because overlaps are rejected on creation. */
	public static Optional<MemberSubscription> currentValid(List<MemberSubscription> subscriptions, LocalDate today) {
		return subscriptions.stream()
				.filter(subscription -> isValid(subscription, today))
				.max(Comparator.comparing(MemberSubscription::getEndDate));
	}

	public static MembershipStatus statusOf(List<MemberSubscription> subscriptions, LocalDate today) {
		Optional<MemberSubscription> valid = currentValid(subscriptions, today);
		if (valid.isPresent()) {
			return isExpiringWithin(valid.get(), today) ? MembershipStatus.EXPIRING : MembershipStatus.ACTIVE;
		}
		if (hasFuturePending(subscriptions, today)) {
			return MembershipStatus.PENDING;
		}
		if (hasEndedBefore(subscriptions, today)) {
			return MembershipStatus.EXPIRED;
		}
		return MembershipStatus.NONE;
	}

	/** True when a valid subscription ends within the expiring window (end &lt;= today + window). */
	public static boolean isExpiringWithin(MemberSubscription valid, LocalDate today) {
		return !valid.getEndDate().isAfter(today.plusDays(EXPIRING_WINDOW_DAYS));
	}

	static boolean hasFuturePending(List<MemberSubscription> subscriptions, LocalDate today) {
		return subscriptions.stream().anyMatch(subscription ->
				isLive(subscription) && subscription.getStartDate().isAfter(today));
	}

	static boolean hasEndedBefore(List<MemberSubscription> subscriptions, LocalDate today) {
		return subscriptions.stream().anyMatch(subscription ->
				subscription.getStatus() != SubscriptionStatus.CANCELLED
						&& subscription.getEndDate().isBefore(today));
	}

}
