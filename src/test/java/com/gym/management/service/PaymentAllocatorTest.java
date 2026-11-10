package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentAllocatorTest {

	private static final List<PaymentAllocator.Open> OPEN = List.of(
			new PaymentAllocator.Open(10L, new BigDecimal("500.00")),
			new PaymentAllocator.Open(11L, new BigDecimal("500.00")),
			new PaymentAllocator.Open(12L, new BigDecimal("500.00")));

	@Test
	void paymentIsAppliedToOldestInstallmentsFirst() {
		List<PaymentAllocator.Share> shares = PaymentAllocator.allocate(new BigDecimal("700.00"), OPEN);

		assertThat(shares).containsExactly(
				new PaymentAllocator.Share(10L, new BigDecimal("500.00")),
				new PaymentAllocator.Share(11L, new BigDecimal("200.00")));
	}

	@Test
	void exactBalancePaysEveryInstallment() {
		List<PaymentAllocator.Share> shares = PaymentAllocator.allocate(new BigDecimal("1500.00"), OPEN);

		assertThat(shares).hasSize(3);
		assertThat(shares.stream().map(PaymentAllocator.Share::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
				.isEqualByComparingTo("1500.00");
	}

	@Test
	void overpaymentIsRejectedAndNothingIsAllocated() {
		assertThatThrownBy(() -> PaymentAllocator.allocate(new BigDecimal("1500.01"), OPEN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("exceeds");
	}

	@Test
	void zeroOrNegativeAmountsAreRejected() {
		assertThatThrownBy(() -> PaymentAllocator.allocate(BigDecimal.ZERO, OPEN))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PaymentAllocator.allocate(new BigDecimal("-5.00"), OPEN))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void installmentsWithNothingOwedAreSkipped() {
		List<PaymentAllocator.Open> withPaidOne = List.of(
				new PaymentAllocator.Open(10L, BigDecimal.ZERO.setScale(2)),
				new PaymentAllocator.Open(11L, new BigDecimal("500.00")));

		assertThat(PaymentAllocator.allocate(new BigDecimal("100.00"), withPaidOne))
				.containsExactly(new PaymentAllocator.Share(11L, new BigDecimal("100.00")));
	}

}
