package com.gym.management.dto;

import com.gym.management.entity.InstallmentStatus;
import com.gym.management.entity.SubscriptionInstallment;
import com.gym.management.service.InstallmentRules;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An installment as shown to clients. status is computed from the dates and amounts, so it is always current.
 * memberId and memberName are filled in by list and detail endpoints; they are null inside a subscription detail.
 */
public record InstallmentResponse(
		Long installmentId,
		Long subscriptionId,
		Long memberId,
		String memberName,
		int installmentNumber,
		LocalDate billingMonth,
		LocalDate dueDate,
		BigDecimal dueAmount,
		BigDecimal paidAmount,
		BigDecimal outstandingAmount,
		InstallmentStatus status
) {

	public static InstallmentResponse of(SubscriptionInstallment installment, LocalDate today) {
		return of(installment, today, null, null);
	}

	public static InstallmentResponse of(SubscriptionInstallment installment, LocalDate today, Long memberId,
			String memberName) {
		return new InstallmentResponse(
				installment.getInstallmentId(),
				installment.getSubscriptionId(),
				memberId,
				memberName,
				installment.getInstallmentNumber(),
				installment.getBillingMonth(),
				installment.getDueDate(),
				installment.getDueAmount(),
				installment.getPaidAmount(),
				InstallmentRules.outstanding(installment.getDueAmount(), installment.getPaidAmount()),
				InstallmentRules.statusFor(installment.getDueAmount(), installment.getPaidAmount(),
						installment.getDueDate(), today));
	}

}
