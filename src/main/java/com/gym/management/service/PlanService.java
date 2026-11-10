package com.gym.management.service;

import com.gym.management.dto.PageResponse;
import com.gym.management.dto.PlanRequests.CreatePlanRequest;
import com.gym.management.dto.PlanRequests.FeatureRequest;
import com.gym.management.dto.PlanRequests.UpdatePlanRequest;
import com.gym.management.dto.PlanResponse;
import com.gym.management.entity.SubscriptionPlan;
import com.gym.management.entity.SubscriptionPlanFeature;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.GymSpecifications;
import com.gym.management.repository.SubscriptionPlanFeatureRepository;
import com.gym.management.repository.SubscriptionPlanRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Subscription plans and their features. Plans are never deleted, because subscriptions refer to them. */
@Service
public class PlanService {

	private final SubscriptionPlanRepository planRepository;
	private final SubscriptionPlanFeatureRepository featureRepository;
	private final Clock clock;

	public PlanService(SubscriptionPlanRepository planRepository, SubscriptionPlanFeatureRepository featureRepository,
			Clock clock) {
		this.planRepository = planRepository;
		this.featureRepository = featureRepository;
		this.clock = clock;
	}

	@Transactional
	public PlanResponse create(CreatePlanRequest request) {
		if (planRepository.existsByPlanCodeAndDurationMonths(request.planCode(), request.durationMonths())) {
			throw new ApiException(HttpStatus.CONFLICT, "A plan with this code and duration already exists.");
		}
		List<FeatureRequest> features = validatedFeatures(request.features());
		LocalDateTime now = LocalDateTime.now(clock);
		SubscriptionPlan plan = planRepository.save(new SubscriptionPlan(
				request.planCode(), request.planName().trim(), blankToNull(request.description()),
				request.durationMonths(), request.price(), true, request.displayOrder(), now));
		List<SubscriptionPlanFeature> saved = saveFeatures(plan.getPlanId(), features, now);
		return toResponse(plan, saved);
	}

	@Transactional(readOnly = true)
	public PageResponse<PlanResponse> list(Boolean active, String search, int page, int size) {
		Page<SubscriptionPlan> result = planRepository.findAll(
				GymSpecifications.planFilter(active, blankToNull(search)),
				PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayOrder")
						.and(Sort.by(Sort.Direction.ASC, "planId"))));
		List<Long> ids = result.getContent().stream().map(SubscriptionPlan::getPlanId).toList();
		Map<Long, List<SubscriptionPlanFeature>> features = ids.isEmpty() ? Map.of()
				: featureRepository.findByPlanIdInOrderByFeatureIdAsc(ids).stream()
						.collect(Collectors.groupingBy(SubscriptionPlanFeature::getPlanId));
		return PageResponse.from(result.map(plan ->
				toResponse(plan, features.getOrDefault(plan.getPlanId(), List.of()))));
	}

	@Transactional(readOnly = true)
	public PlanResponse get(long planId) {
		SubscriptionPlan plan = findPlan(planId);
		return toResponse(plan, featureRepository.findByPlanIdOrderByFeatureIdAsc(planId));
	}

	@Transactional
	public PlanResponse update(long planId, UpdatePlanRequest request) {
		SubscriptionPlan plan = findPlan(planId);
		if (planRepository.existsByPlanCodeAndDurationMonthsAndPlanIdNot(request.planCode(), request.durationMonths(),
				planId)) {
			throw new ApiException(HttpStatus.CONFLICT, "A plan with this code and duration already exists.");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		plan.update(request.planCode(), request.planName().trim(), blankToNull(request.description()),
				request.durationMonths(), request.price(), request.displayOrder(), now);

		List<SubscriptionPlanFeature> features;
		if (request.features() == null) {
			features = featureRepository.findByPlanIdOrderByFeatureIdAsc(planId);
		} else {
			// Replace the whole list. Deletes are flushed before inserts, so the unique (plan_id, feature_name) key holds.
			List<SubscriptionPlanFeature> existing = featureRepository.findByPlanIdOrderByFeatureIdAsc(planId);
			featureRepository.deleteAll(existing);
			featureRepository.flush();
			features = saveFeatures(planId, validatedFeatures(request.features()), now);
		}
		return toResponse(plan, features);
	}

	@Transactional
	public PlanResponse setActive(long planId, boolean active) {
		SubscriptionPlan plan = findPlan(planId);
		plan.changeActive(active, LocalDateTime.now(clock));
		return toResponse(plan, featureRepository.findByPlanIdOrderByFeatureIdAsc(planId));
	}

	/** Loads a plan or reports it as not found. */
	SubscriptionPlan findPlan(long planId) {
		return planRepository.findById(planId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));
	}

	private List<SubscriptionPlanFeature> saveFeatures(Long planId, List<FeatureRequest> features, LocalDateTime now) {
		return featureRepository.saveAll(features.stream()
				.map(feature -> new SubscriptionPlanFeature(planId, feature.featureName().trim(),
						blankToNull(feature.featureDescription()),
						feature.included() == null || feature.included(), now))
				.toList());
	}

	/** Trims names, and rejects blank or duplicate names, so the (plan_id, feature_name) unique key is never hit. */
	static List<FeatureRequest> validatedFeatures(List<FeatureRequest> features) {
		if (features == null) {
			return List.of();
		}
		Set<String> names = new HashSet<>();
		for (FeatureRequest feature : features) {
			String name = feature.featureName() == null ? "" : feature.featureName().trim();
			if (name.isEmpty()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Feature name must not be blank.");
			}
			if (!names.add(name.toLowerCase(java.util.Locale.ROOT))) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Feature names must be unique within a plan.");
			}
		}
		return features;
	}

	static PlanResponse toResponse(SubscriptionPlan plan, List<SubscriptionPlanFeature> features) {
		return new PlanResponse(
				plan.getPlanId(),
				plan.getPlanCode(),
				plan.getPlanName(),
				plan.getDescription(),
				plan.getDurationMonths(),
				plan.getPrice(),
				plan.isActive(),
				plan.getDisplayOrder(),
				features.stream()
						.map(feature -> new PlanResponse.Feature(feature.getFeatureId(), feature.getFeatureName(),
								feature.getFeatureDescription(), feature.isIncluded()))
						.toList(),
				plan.getCreatedAt(),
				plan.getUpdatedAt());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
