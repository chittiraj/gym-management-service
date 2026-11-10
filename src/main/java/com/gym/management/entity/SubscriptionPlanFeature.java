package com.gym.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps the existing subscription_plan_features table. plan_id has a foreign key to subscription_plans. */
@Entity
@Table(name = "subscription_plan_features")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlanFeature {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "feature_id")
	private Long featureId;

	@Column(name = "plan_id", nullable = false)
	private Long planId;

	@Column(name = "feature_name", nullable = false, length = 150)
	private String featureName;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "feature_description", columnDefinition = "TEXT")
	private String featureDescription;

	@Column(name = "is_included", nullable = false)
	private boolean included;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public SubscriptionPlanFeature(Long planId, String featureName, String featureDescription, boolean included,
			LocalDateTime now) {
		this.planId = planId;
		this.featureName = featureName;
		this.featureDescription = featureDescription;
		this.included = included;
		this.createdAt = now;
		this.updatedAt = now;
	}

}
