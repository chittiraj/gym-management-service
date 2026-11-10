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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps the existing subscription_plans table. The schema is owned by the database and is not altered. */
@Entity
@Table(name = "subscription_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "plan_id")
	private Long planId;

	@Column(name = "plan_code", nullable = false, length = 30)
	private String planCode;

	@Column(name = "plan_name", nullable = false, length = 100)
	private String planName;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "duration_months", nullable = false)
	private int durationMonths;

	@Column(name = "price", nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "display_order")
	private Integer displayOrder;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public SubscriptionPlan(String planCode, String planName, String description, int durationMonths,
			BigDecimal price, boolean active, Integer displayOrder, LocalDateTime now) {
		this.planCode = planCode;
		this.planName = planName;
		this.description = description;
		this.durationMonths = durationMonths;
		this.price = price;
		this.active = active;
		this.displayOrder = displayOrder;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Changes plan details. Existing subscriptions keep their own snapshots and amounts. */
	public void update(String planCode, String planName, String description, int durationMonths, BigDecimal price,
			Integer displayOrder, LocalDateTime now) {
		this.planCode = planCode;
		this.planName = planName;
		this.description = description;
		this.durationMonths = durationMonths;
		this.price = price;
		this.displayOrder = displayOrder;
		this.updatedAt = now;
	}

	public void changeActive(boolean active, LocalDateTime now) {
		this.active = active;
		this.updatedAt = now;
	}

}
