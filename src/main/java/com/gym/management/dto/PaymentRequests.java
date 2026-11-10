package com.gym.management.dto;

import com.gym.management.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Request bodies for recording payments. The member is taken from the subscription, never from the client. */
public final class PaymentRequests {

	private PaymentRequests() {
	}

	/**
	 * Records a successful payment against a subscription. The amount is applied to the oldest unpaid installments
	 * first and cannot exceed what is still owed. paymentDate defaults to now and cannot be in the future.
	 */
	public record RecordPaymentRequest(
			@NotNull @Positive Long subscriptionId,
			@NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
			@NotNull PaymentMethod paymentMethod,
			@Size(max = 150) String transactionReference,
			@Size(max = 2000) String remarks,
			LocalDateTime paymentDate
	) {
	}

	/** Records a payment against one specific installment. The amount cannot exceed that installment's balance. */
	public record RecordInstallmentPaymentRequest(
			@NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
			@NotNull PaymentMethod paymentMethod,
			@Size(max = 150) String transactionReference,
			@Size(max = 2000) String remarks,
			LocalDateTime paymentDate
	) {
	}

}
