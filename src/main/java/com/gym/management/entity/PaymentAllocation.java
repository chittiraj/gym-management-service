package com.gym.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Maps payment_allocations. Each row links part of one payment to one installment. allocated_amount is always > 0. */
@Entity
@Table(name = "payment_allocations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "allocation_id")
	private Long allocationId;

	@Column(name = "payment_id", nullable = false)
	private Long paymentId;

	@Column(name = "installment_id", nullable = false)
	private Long installmentId;

	@Column(name = "allocated_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal allocatedAmount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public PaymentAllocation(Long paymentId, Long installmentId, BigDecimal allocatedAmount, LocalDateTime now) {
		this.paymentId = paymentId;
		this.installmentId = installmentId;
		this.allocatedAmount = allocatedAmount;
		this.createdAt = now;
	}

}
