package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gym.management.dto.PlanRequests.CreatePlanRequest;
import com.gym.management.dto.PlanRequests.FeatureRequest;
import com.gym.management.dto.PlanRequests.UpdatePlanRequest;
import com.gym.management.dto.PlanResponse;
import com.gym.management.entity.SubscriptionPlan;
import com.gym.management.entity.SubscriptionPlanFeature;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.SubscriptionPlanFeatureRepository;
import com.gym.management.repository.SubscriptionPlanRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 10, 10, 0);

	@Mock
	SubscriptionPlanRepository planRepository;
	@Mock
	SubscriptionPlanFeatureRepository featureRepository;

	private PlanService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
		service = new PlanService(planRepository, featureRepository, clock);
	}

	private static SubscriptionPlan plan(long id) {
		SubscriptionPlan plan = new SubscriptionPlan("MONTHLY", "Monthly", null, 1, new BigDecimal("1500.00"), true,
				1, NOW);
		ReflectionTestUtils.setField(plan, "planId", id);
		return plan;
	}

	private static CreatePlanRequest create(List<FeatureRequest> features) {
		return new CreatePlanRequest("MONTHLY", "Monthly", "One month", 1, new BigDecimal("1500.00"), 1, features);
	}

	@Test
	void createSavesPlanAndFeaturesAndReturnsThem() {
		when(planRepository.existsByPlanCodeAndDurationMonths("MONTHLY", 1)).thenReturn(false);
		when(planRepository.save(any(SubscriptionPlan.class))).thenAnswer(invocation -> {
			SubscriptionPlan saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "planId", 3L);
			return saved;
		});
		when(featureRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

		PlanResponse response = service.create(create(List.of(
				new FeatureRequest("Locker", "Personal locker", null),
				new FeatureRequest("Sauna", null, false))));

		assertThat(response.planId()).isEqualTo(3L);
		assertThat(response.isActive()).isTrue();
		assertThat(response.features()).extracting(PlanResponse.Feature::featureName)
				.containsExactly("Locker", "Sauna");
		assertThat(response.features().get(0).included()).isTrue();
		assertThat(response.features().get(1).included()).isFalse();
	}

	@Test
	void duplicateCodeAndDurationIsConflict() {
		when(planRepository.existsByPlanCodeAndDurationMonths("MONTHLY", 1)).thenReturn(true);

		assertThatThrownBy(() -> service.create(create(null)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
		verify(planRepository, never()).save(any());
	}

	@Test
	void duplicateFeatureNamesAreRejectedBeforeSaving() {
		when(planRepository.existsByPlanCodeAndDurationMonths("MONTHLY", 1)).thenReturn(false);

		assertThatThrownBy(() -> service.create(create(List.of(
				new FeatureRequest("Locker", null, null),
				new FeatureRequest(" locker ", null, null)))))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("unique");
		verify(planRepository, never()).save(any());
	}

	@Test
	void blankFeatureNameIsRejected() {
		assertThatThrownBy(() -> PlanService.validatedFeatures(List.of(new FeatureRequest("   ", null, null))))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void updateReplacesFeatureListByDeletingThenFlushingThenInserting() {
		when(planRepository.findById(3L)).thenReturn(Optional.of(plan(3L)));
		when(planRepository.existsByPlanCodeAndDurationMonthsAndPlanIdNot("MONTHLY", 1, 3L)).thenReturn(false);
		SubscriptionPlanFeature old = new SubscriptionPlanFeature(3L, "Old", null, true, NOW);
		when(featureRepository.findByPlanIdOrderByFeatureIdAsc(3L)).thenReturn(List.of(old));
		when(featureRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

		service.update(3L, new UpdatePlanRequest("MONTHLY", "Monthly plan", null, 1, new BigDecimal("1600.00"), 2,
				List.of(new FeatureRequest("New", null, true))));

		InOrder order = inOrder(featureRepository);
		order.verify(featureRepository).deleteAll(List.of(old));
		order.verify(featureRepository).flush();
		order.verify(featureRepository).saveAll(anyList());
	}

	@Test
	void updateWithoutFeaturesKeepsExistingFeatures() {
		when(planRepository.findById(3L)).thenReturn(Optional.of(plan(3L)));
		when(planRepository.existsByPlanCodeAndDurationMonthsAndPlanIdNot("MONTHLY", 1, 3L)).thenReturn(false);
		when(featureRepository.findByPlanIdOrderByFeatureIdAsc(3L)).thenReturn(List.of());

		service.update(3L, new UpdatePlanRequest("MONTHLY", "Monthly", null, 1, new BigDecimal("1600.00"), 2, null));

		verify(featureRepository, never()).deleteAll(anyList());
		verify(featureRepository, never()).saveAll(anyList());
	}

	@Test
	void updateToAnExistingCodeAndDurationIsConflict() {
		when(planRepository.findById(3L)).thenReturn(Optional.of(plan(3L)));
		when(planRepository.existsByPlanCodeAndDurationMonthsAndPlanIdNot("MONTHLY", 1, 3L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(3L,
				new UpdatePlanRequest("MONTHLY", "Monthly", null, 1, new BigDecimal("1600.00"), 2, null)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void deactivatingAPlanChangesOnlyItsFlag() {
		SubscriptionPlan plan = plan(3L);
		when(planRepository.findById(3L)).thenReturn(Optional.of(plan));
		when(featureRepository.findByPlanIdOrderByFeatureIdAsc(3L)).thenReturn(List.of());

		PlanResponse response = service.setActive(3L, false);

		assertThat(response.isActive()).isFalse();
		assertThat(plan.getPrice()).isEqualByComparingTo("1500.00");
	}

	@Test
	void unknownPlanIsNotFound() {
		when(planRepository.findById(anyLong())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(404L))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

}
