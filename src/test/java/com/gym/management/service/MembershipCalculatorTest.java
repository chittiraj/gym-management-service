package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.PaymentOption;
import com.gym.management.entity.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MembershipCalculatorTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	private static MemberSubscription sub(SubscriptionStatus status, LocalDate start, LocalDate end) {
		return new MemberSubscription(1L, 1L, "MONTHLY", "Monthly", 1, new BigDecimal("1500.00"),
				PaymentOption.FULL, start, end, status, LocalDateTime.now());
	}

	@Test
	void noSubscriptionsIsNone() {
		assertThat(MembershipCalculator.statusOf(List.of(), TODAY)).isEqualTo(MembershipStatus.NONE);
	}

	@Test
	void validSubscriptionEndingFarAheadIsActive() {
		MemberSubscription s = sub(SubscriptionStatus.ACTIVE, TODAY.minusDays(5), TODAY.plusDays(30));
		assertThat(MembershipCalculator.statusOf(List.of(s), TODAY)).isEqualTo(MembershipStatus.ACTIVE);
	}

	@Test
	void endingWithinSevenDaysIsExpiringIncludingExactlySevenDays() {
		MemberSubscription seven = sub(SubscriptionStatus.ACTIVE, TODAY.minusDays(5), TODAY.plusDays(7));
		assertThat(MembershipCalculator.statusOf(List.of(seven), TODAY)).isEqualTo(MembershipStatus.EXPIRING);
	}

	@Test
	void endingEightDaysAheadIsStillActive() {
		MemberSubscription eight = sub(SubscriptionStatus.ACTIVE, TODAY.minusDays(5), TODAY.plusDays(8));
		assertThat(MembershipCalculator.statusOf(List.of(eight), TODAY)).isEqualTo(MembershipStatus.ACTIVE);
	}

	@Test
	void endingTodayIsExpiringNotExpired() {
		MemberSubscription today = sub(SubscriptionStatus.ACTIVE, TODAY.minusDays(30), TODAY);
		assertThat(MembershipCalculator.statusOf(List.of(today), TODAY)).isEqualTo(MembershipStatus.EXPIRING);
	}

	@Test
	void endedYesterdayIsExpired() {
		MemberSubscription ended = sub(SubscriptionStatus.ACTIVE, TODAY.minusDays(30), TODAY.minusDays(1));
		assertThat(MembershipCalculator.statusOf(List.of(ended), TODAY)).isEqualTo(MembershipStatus.EXPIRED);
	}

	@Test
	void startedPendingSubscriptionCountsAsValidBecauseDatesDecide() {
		// The stored status is not refreshed when a start date passes, so a PENDING row that has started is valid.
		MemberSubscription started = sub(SubscriptionStatus.PENDING, TODAY.minusDays(1), TODAY.plusDays(29));
		assertThat(MembershipCalculator.statusOf(List.of(started), TODAY)).isEqualTo(MembershipStatus.ACTIVE);
	}

	@Test
	void futureSubscriptionIsPending() {
		MemberSubscription future = sub(SubscriptionStatus.PENDING, TODAY.plusDays(3), TODAY.plusDays(33));
		assertThat(MembershipCalculator.statusOf(List.of(future), TODAY)).isEqualTo(MembershipStatus.PENDING);
	}

	@Test
	void cancelledSubscriptionsAreIgnored() {
		MemberSubscription cancelled = sub(SubscriptionStatus.CANCELLED, TODAY.minusDays(5), TODAY.plusDays(30));
		assertThat(MembershipCalculator.statusOf(List.of(cancelled), TODAY)).isEqualTo(MembershipStatus.NONE);
	}

	@Test
	void pastRenewalDoesNotHideAnotherPendingFutureSubscription() {
		MemberSubscription old = sub(SubscriptionStatus.EXPIRED, TODAY.minusDays(60), TODAY.minusDays(10));
		MemberSubscription next = sub(SubscriptionStatus.PENDING, TODAY.plusDays(1), TODAY.plusDays(31));
		assertThat(MembershipCalculator.statusOf(List.of(old, next), TODAY)).isEqualTo(MembershipStatus.PENDING);
	}

	@Test
	void currentValidPicksTheLatestEndingOne() {
		MemberSubscription earlier = sub(SubscriptionStatus.ACTIVE, TODAY.minusDays(5), TODAY.plusDays(10));
		MemberSubscription later = sub(SubscriptionStatus.ACTIVE, TODAY.minusDays(5), TODAY.plusDays(40));
		assertThat(MembershipCalculator.currentValid(List.of(earlier, later), TODAY)).contains(later);
	}

}
