package com.gym.management.dto;

import com.gym.management.entity.PaymentMethod;
import com.gym.management.entity.PaymentOption;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Receipt for one successful payment. Built only from stored rows. invoiceNumber is derived from the payment id
 * and is not stored, so no schema change is needed.
 */
public record InvoiceResponse(
		String invoiceNumber,
		Long paymentId,
		LocalDateTime paymentDate,
		BigDecimal amountPaid,
		PaymentMethod paymentMethod,
		String transactionReference,
		String remarks,
		String receivedByName,
		Member member,
		Subscription subscription,
		List<Line> lines,
		BigDecimal outstandingBalanceNow
) {

	public record Member(Long userId, String fullName, String email, String phone) {
	}

	public record Subscription(
			Long subscriptionId,
			String planCode,
			String planName,
			int durationMonths,
			BigDecimal totalAmount,
			PaymentOption paymentOption,
			LocalDate startDate,
			LocalDate endDate
	) {
	}

	/** One installment this payment was applied to. */
	public record Line(int installmentNumber, LocalDate dueDate, BigDecimal allocatedAmount) {
	}

}
