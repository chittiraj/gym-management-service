package com.gym.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps subscription_installments. The schema enforces 0 <= paid_amount <= due_amount. paid_amount is changed only
 * through applyAllocation, which runs inside the payment transaction after the subscription row is locked.
 */
@Entity
@Table(name = "subscription_installments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionInstallment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "installment_id")
	private Long installmentId;

	@Column(name = "subscription_id", nullable = false)
	private Long subscriptionId;

	@Column(name = "installment_number", nullable = false)
	private int installmentNumber;

	@Column(name = "billing_month", nullable = false)
	private LocalDate billingMonth;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(name = "due_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal dueAmount;

	@Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal paidAmount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.ENUM)
	@Column(name = "status", nullable = false)
	private InstallmentStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public SubscriptionInstallment(Long subscriptionId, int installmentNumber, LocalDate billingMonth,
			LocalDate dueDate, BigDecimal dueAmount, LocalDateTime now) {
		this.subscriptionId = subscriptionId;
		this.installmentNumber = installmentNumber;
		this.billingMonth = billingMonth;
		this.dueDate = dueDate;
		this.dueAmount = dueAmount;
		this.paidAmount = BigDecimal.ZERO.setScale(2);
		this.status = InstallmentStatus.UPCOMING;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Stores the new paid total and the status derived from it. Callers must have checked the amounts. */
	public void applyAllocation(BigDecimal newPaidAmount, InstallmentStatus newStatus, LocalDateTime now) {
		this.paidAmount = newPaidAmount;
		this.status = newStatus;
		this.updatedAt = now;
	}

	public BigDecimal getOutstandingAmount() {
		return dueAmount.subtract(paidAmount);
	}

}
