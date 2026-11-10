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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps the existing payments table. Payments are never deleted. Status changes are recorded on the row. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "payment_id")
	private Long paymentId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "subscription_id", nullable = false)
	private Long subscriptionId;

	@Column(name = "amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.ENUM)
	@Column(name = "payment_method", nullable = false)
	private PaymentMethod paymentMethod;

	@Column(name = "transaction_reference", length = 150)
	private String transactionReference;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.ENUM)
	@Column(name = "payment_status", nullable = false)
	private PaymentStatus paymentStatus;

	@Column(name = "payment_date", nullable = false)
	private LocalDateTime paymentDate;

	@Column(name = "received_by")
	private Long receivedBy;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "remarks", columnDefinition = "TEXT")
	private String remarks;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public Payment(Long userId, Long subscriptionId, BigDecimal amount, PaymentMethod paymentMethod,
			String transactionReference, PaymentStatus paymentStatus, LocalDateTime paymentDate, Long receivedBy,
			String remarks, LocalDateTime now) {
		this.userId = userId;
		this.subscriptionId = subscriptionId;
		this.amount = amount;
		this.paymentMethod = paymentMethod;
		this.transactionReference = transactionReference;
		this.paymentStatus = paymentStatus;
		this.paymentDate = paymentDate;
		this.receivedBy = receivedBy;
		this.remarks = remarks;
		this.createdAt = now;
		this.updatedAt = now;
	}

}
