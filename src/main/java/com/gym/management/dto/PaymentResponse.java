package com.gym.management.dto;

import com.gym.management.entity.PaymentMethod;
import com.gym.management.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentResponse(
		Long paymentId,
		Long userId,
		String memberName,
		Long subscriptionId,
		BigDecimal amount,
		PaymentMethod paymentMethod,
		String transactionReference,
		PaymentStatus paymentStatus,
		LocalDateTime paymentDate,
		Long receivedBy,
		String remarks,
		List<Allocation> allocations
) {

	/** How much of this payment went to one installment. */
	public record Allocation(Long installmentId, int installmentNumber, BigDecimal allocatedAmount) {
	}

}
