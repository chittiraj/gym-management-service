package com.gym.management.dto;

import com.gym.management.entity.PaymentOption;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/** Request bodies for subscriptions. */
public final class SubscriptionRequests {

	private SubscriptionRequests() {
	}

	/**
	 * Assigns a plan to a MEMBER account. startDate defaults to today and cannot be earlier than today or more
	 * than a year ahead. The role is never taken from the request.
	 */
	public record CreateSubscriptionRequest(
			@NotNull @Positive Long userId,
			@NotNull @Positive Long planId,
			@NotNull PaymentOption paymentOption,
			LocalDate startDate
	) {
	}

	/**
	 * Renews a subscription by creating a new one. planId defaults to the original plan. The new subscription starts
	 * the day after the current end date, or today if that date has passed. The old record is kept.
	 */
	public record RenewSubscriptionRequest(
			@Positive Long planId,
			@NotNull PaymentOption paymentOption
	) {
	}

}
