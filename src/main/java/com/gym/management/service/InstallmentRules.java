package com.gym.management.service;

import com.gym.management.entity.InstallmentStatus;
import com.gym.management.entity.PaymentOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Installment schedule and status rules. Money is kept at scale 2. The schedule rounds each regular installment
 * down, so the final installment absorbs the remainder and the sum always equals the total.
 */
public final class InstallmentRules {

	private static final BigDecimal MIN_INSTALLMENT = new BigDecimal("0.01");

	private InstallmentRules() {
	}

	/** One planned installment before it is stored. */
	public record Draft(int installmentNumber, LocalDate billingMonth, LocalDate dueDate, BigDecimal dueAmount) {
	}

	/**
	 * FULL: one installment for the whole amount, due on the start date. INSTALLMENT: one installment per month
	 * of the plan, due on the same day of each month. Throws IllegalArgumentException if the amounts are invalid.
	 */
	public static List<Draft> schedule(BigDecimal total, int durationMonths, PaymentOption option, LocalDate startDate) {
		if (total == null || total.signum() <= 0) {
			throw new IllegalArgumentException("Total amount must be positive");
		}
		int count = option == PaymentOption.FULL ? 1 : durationMonths;
		if (count < 1) {
			throw new IllegalArgumentException("Duration must be at least one month");
		}
		BigDecimal regular = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
		BigDecimal last = total.subtract(regular.multiply(BigDecimal.valueOf(count - 1L)));
		if (regular.compareTo(MIN_INSTALLMENT) < 0 || last.compareTo(MIN_INSTALLMENT) < 0) {
			throw new IllegalArgumentException("Total amount is too small to split into the required installments");
		}
		List<Draft> drafts = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			LocalDate dueDate = option == PaymentOption.FULL ? startDate : startDate.plusMonths(index);
			BigDecimal amount = index == count - 1 ? last : regular;
			drafts.add(new Draft(index + 1, dueDate.withDayOfMonth(1), dueDate, amount));
		}
		return drafts;
	}

	/**
	 * Status for an installment given its amounts and today's date. PAID when fully paid. Otherwise OVERDUE when
	 * the due date has passed, PARTIAL when some has been paid, and UPCOMING when nothing has been paid.
	 */
	public static InstallmentStatus statusFor(BigDecimal dueAmount, BigDecimal paidAmount, LocalDate dueDate,
			LocalDate today) {
		if (paidAmount.compareTo(dueAmount) >= 0) {
			return InstallmentStatus.PAID;
		}
		if (dueDate.isBefore(today)) {
			return InstallmentStatus.OVERDUE;
		}
		return paidAmount.signum() > 0 ? InstallmentStatus.PARTIAL : InstallmentStatus.UPCOMING;
	}

	public static BigDecimal outstanding(BigDecimal dueAmount, BigDecimal paidAmount) {
		return dueAmount.subtract(paidAmount);
	}

}
