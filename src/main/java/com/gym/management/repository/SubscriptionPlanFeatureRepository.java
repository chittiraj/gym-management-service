package com.gym.management.repository;

import com.gym.management.entity.SubscriptionPlanFeature;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanFeatureRepository extends JpaRepository<SubscriptionPlanFeature, Long> {

	/** One query for every plan on a page, so listing plans does not query features once per plan. */
	List<SubscriptionPlanFeature> findByPlanIdInOrderByFeatureIdAsc(Collection<Long> planIds);

	List<SubscriptionPlanFeature> findByPlanIdOrderByFeatureIdAsc(Long planId);

}
