package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gym.management.entity.InstallmentStatus;
import com.gym.management.entity.PaymentOption;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallmentRulesTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 31);
	private static final LocalDate TODAY = LocalDate.of(2026, 2, 10);

	private static BigDecimal sum(List<InstallmentRules.Draft> drafts) {
		return drafts.stream().map(InstallmentRules.Draft::dueAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	@Test
	void fullOptionIsOneInstallmentForTheWholeAmountDueOnStart() {
		List<InstallmentRules.Draft> drafts = InstallmentRules.schedule(new BigDecimal("4500.00"), 3,
				PaymentOption.FULL, START);

		assertThat(drafts).hasSize(1);
		assertThat(drafts.get(0).dueAmount()).isEqualByComparingTo("4500.00");
		assertThat(drafts.get(0).dueDate()).isEqualTo(START);
		assertThat(drafts.get(0).billingMonth()).isEqualTo(LocalDate.of(2026, 1, 1));
	}

	@Test
	void installmentOptionCreatesOneInstallmentPerMonth() {
		List<InstallmentRules.Draft> drafts = InstallmentRules.schedule(new BigDecimal("3000.00"), 3,
				PaymentOption.INSTALLMENT, LocalDate.of(2026, 1, 15));

		assertThat(drafts).hasSize(3);
		assertThat(drafts).extracting(InstallmentRules.Draft::installmentNumber).containsExactly(1, 2, 3);
		assertThat(drafts).extracting(InstallmentRules.Draft::dueDate).containsExactly(
				LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 15));
		assertThat(drafts).extracting(InstallmentRules.Draft::billingMonth).containsExactly(
				LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
	}

	@Test
	void lastInstallmentAbsorbsTheRoundingRemainderSoTheSumIsExact() {
		// 1000.00 / 3 rounds down to 333.33 each, so the third installment is 333.34.
		List<InstallmentRules.Draft> drafts = InstallmentRules.schedule(new BigDecimal("1000.00"), 3,
				PaymentOption.INSTALLMENT, START);

		assertThat(drafts.get(0).dueAmount()).isEqualByComparingTo("333.33");
		assertThat(drafts.get(1).dueAmount()).isEqualByComparingTo("333.33");
		assertThat(drafts.get(2).dueAmount()).isEqualByComparingTo("333.34");
		assertThat(sum(drafts)).isEqualByComparingTo("1000.00");
	}

	@Test
	void monthEndStartDoesNotOverflowIntoNextMonthOnLaterDueDates() {
		List<InstallmentRules.Draft> drafts = InstallmentRules.schedule(new BigDecimal("600.00"), 2,
				PaymentOption.INSTALLMENT, START);

		assertThat(drafts.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
	}

	@Test
	void rejectsNonPositiveTotalsAndAmountsTooSmallToSplit() {
		assertThatThrownBy(() -> InstallmentRules.schedule(BigDecimal.ZERO, 1, PaymentOption.FULL, START))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> InstallmentRules.schedule(new BigDecimal("0.02"), 3, PaymentOption.INSTALLMENT, START))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void statusIsPaidWhenFullyPaidRegardlessOfDate() {
		assertThat(InstallmentRules.statusFor(new BigDecimal("500.00"), new BigDecimal("500.00"),
				LocalDate.of(2025, 1, 1), TODAY)).isEqualTo(InstallmentStatus.PAID);
	}

	@Test
	void unpaidInstallmentPastDueIsOverdueEvenWhenPartlyPaid() {
		assertThat(InstallmentRules.statusFor(new BigDecimal("500.00"), BigDecimal.ZERO.setScale(2),
				LocalDate.of(2026, 2, 1), TODAY)).isEqualTo(InstallmentStatus.OVERDUE);
		assertThat(InstallmentRules.statusFor(new BigDecimal("500.00"), new BigDecimal("100.00"),
				LocalDate.of(2026, 2, 1), TODAY)).isEqualTo(InstallmentStatus.OVERDUE);
	}

	@Test
	void partlyPaidFutureInstallmentIsPartialAndUnpaidFutureIsUpcoming() {
		assertThat(InstallmentRules.statusFor(new BigDecimal("500.00"), new BigDecimal("100.00"),
				LocalDate.of(2026, 3, 1), TODAY)).isEqualTo(InstallmentStatus.PARTIAL);
		assertThat(InstallmentRules.statusFor(new BigDecimal("500.00"), BigDecimal.ZERO.setScale(2),
				LocalDate.of(2026, 3, 1), TODAY)).isEqualTo(InstallmentStatus.UPCOMING);
	}

	@Test
	void dueTodayIsNotOverdue() {
		assertThat(InstallmentRules.statusFor(new BigDecimal("500.00"), BigDecimal.ZERO.setScale(2), TODAY, TODAY))
				.isEqualTo(InstallmentStatus.UPCOMING);
	}

}
