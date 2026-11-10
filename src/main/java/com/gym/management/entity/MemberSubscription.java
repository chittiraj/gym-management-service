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
 * Maps member_subscriptions. user_id and plan_id are plain columns rather than associations, which avoids lazy
 * loading and keeps queries explicit. The plan_*_snapshot and total_amount columns are copied when the
 * subscription is created, so later plan edits do not change history.
 */
@Entity
@Table(name = "member_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "subscription_id")
	private Long subscriptionId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "plan_id", nullable = false)
	private Long planId;

	@Column(name = "plan_code_snapshot", nullable = false, length = 30)
	private String planCodeSnapshot;

	@Column(name = "plan_name_snapshot", nullable = false, length = 100)
	private String planNameSnapshot;

	@Column(name = "duration_months", nullable = false)
	private int durationMonths;

	@Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.ENUM)
	@Column(name = "payment_option", nullable = false)
	private PaymentOption paymentOption;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.ENUM)
	@Column(name = "status", nullable = false)
	private SubscriptionStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public MemberSubscription(Long userId, Long planId, String planCodeSnapshot, String planNameSnapshot,
			int durationMonths, BigDecimal totalAmount, PaymentOption paymentOption, LocalDate startDate,
			LocalDate endDate, SubscriptionStatus status, LocalDateTime now) {
		this.userId = userId;
		this.planId = planId;
		this.planCodeSnapshot = planCodeSnapshot;
		this.planNameSnapshot = planNameSnapshot;
		this.durationMonths = durationMonths;
		this.totalAmount = totalAmount;
		this.paymentOption = paymentOption;
		this.startDate = startDate;
		this.endDate = endDate;
		this.status = status;
		this.createdAt = now;
		this.updatedAt = now;
	}

}
