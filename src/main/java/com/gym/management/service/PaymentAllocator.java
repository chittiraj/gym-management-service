package com.gym.management.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits one payment across installments, oldest first. Callers must already have checked that the amount does
 * not exceed the total outstanding balance, so the result always fully allocates the payment.
 */
public final class PaymentAllocator {

	private PaymentAllocator() {
	}

	/** An installment that still has money owed, identified by its id. */
	public record Open(Long installmentId, BigDecimal outstanding) {
	}

	/** Amount applied to one installment. */
	public record Share(Long installmentId, BigDecimal amount) {
	}

	/** Shares for amount, taking installments in the order given. Throws if the amount exceeds what is owed. */
	public static List<Share> allocate(BigDecimal amount, List<Open> openInstallments) {
		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException("Payment amount must be positive");
		}
		BigDecimal remaining = amount;
		List<Share> shares = new ArrayList<>();
		for (Open open : openInstallments) {
			if (remaining.signum() == 0) {
				break;
			}
			if (open.outstanding().signum() <= 0) {
				continue;
			}
			BigDecimal share = remaining.min(open.outstanding());
			shares.add(new Share(open.installmentId(), share));
			remaining = remaining.subtract(share);
		}
		if (remaining.signum() != 0) {
			throw new IllegalArgumentException("Payment exceeds the outstanding balance");
		}
		return shares;
	}

}
