package com.gym.management.repository;

import com.gym.management.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long>,
		JpaSpecificationExecutor<SubscriptionPlan> {

	/** Plan code and duration form the table's unique key (uk_plan_code_duration). */
	boolean existsByPlanCodeAndDurationMonths(String planCode, int durationMonths);

	boolean existsByPlanCodeAndDurationMonthsAndPlanIdNot(String planCode, int durationMonths, Long planId);

}
