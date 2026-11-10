package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gym.management.dto.SubscriptionRequests.CreateSubscriptionRequest;
import com.gym.management.dto.SubscriptionRequests.RenewSubscriptionRequest;
import com.gym.management.dto.SubscriptionResponse;
import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.PaymentOption;
import com.gym.management.entity.Role;
import com.gym.management.entity.SubscriptionPlan;
import com.gym.management.entity.SubscriptionStatus;
import com.gym.management.entity.User;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.MemberSubscriptionRepository;
import com.gym.management.repository.SubscriptionInstallmentRepository;
import com.gym.management.repository.SubscriptionPlanRepository;
import com.gym.management.repository.UserRepository;
import com.gym.management.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 10, 10, 0);
	private static final LocalDate TODAY = LocalDate.of(2026, 2, 10);

	@Mock
	MemberSubscriptionRepository subscriptionRepository;
	@Mock
	SubscriptionInstallmentRepository installmentRepository;
	@Mock
	SubscriptionPlanRepository planRepository;
	@Mock
	UserRepository userRepository;

	private SubscriptionService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
		service = new SubscriptionService(subscriptionRepository, installmentRepository, planRepository,
				userRepository, clock);
	}

	private static User user(long id, Role role, boolean active) {
		User user = BeanUtils.instantiateClass(User.class);
		ReflectionTestUtils.setField(user, "userId", id);
		ReflectionTestUtils.setField(user, "fullName", "Member " + id);
		ReflectionTestUtils.setField(user, "email", "m" + id + "@gmail.com");
		ReflectionTestUtils.setField(user, "role", role);
		ReflectionTestUtils.setField(user, "active", active);
		return user;
	}

	private static SubscriptionPlan plan(boolean active) {
		SubscriptionPlan plan = new SubscriptionPlan("QUARTERLY", "Quarterly", "Three months", 3,
				new BigDecimal("3000.00"), active, 1, NOW);
		ReflectionTestUtils.setField(plan, "planId", 1L);
		return plan;
	}

	private static MemberSubscription subscription(long id, SubscriptionStatus status, LocalDate start,
			LocalDate end) {
		MemberSubscription subscription = new MemberSubscription(5L, 1L, "QUARTERLY", "Quarterly", 3,
				new BigDecimal("3000.00"), PaymentOption.INSTALLMENT, start, end, status, NOW);
		ReflectionTestUtils.setField(subscription, "subscriptionId", id);
		return subscription;
	}

	private void stubSaves() {
		when(subscriptionRepository.save(any(MemberSubscription.class))).thenAnswer(invocation -> {
			MemberSubscription saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "subscriptionId", 100L);
			return saved;
		});
		when(installmentRepository.saveAll(anyList())).thenAnswer(invocation -> {
			List<?> list = invocation.getArgument(0);
			return list;
		});
	}

	// ---------- assignment ----------

	@Test
	void assignmentCreatesSubscriptionWithSnapshotsAndThreeMonthlyInstallments() {
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.MEMBER, true)));
		when(planRepository.findById(1L)).thenReturn(Optional.of(plan(true)));
		when(subscriptionRepository.existsOverlapping(eq(5L), any(), any(), eq(SubscriptionStatus.CANCELLED)))
				.thenReturn(false);
		stubSaves();

		SubscriptionResponse.Detail detail = service.create(
				new CreateSubscriptionRequest(5L, 1L, PaymentOption.INSTALLMENT, null));

		SubscriptionResponse summary = detail.subscription();
		assertThat(summary.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(summary.effectiveStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(summary.startDate()).isEqualTo(TODAY);
		assertThat(summary.endDate()).isEqualTo(LocalDate.of(2026, 5, 9));
		assertThat(summary.planCode()).isEqualTo("QUARTERLY");
		assertThat(summary.totalAmount()).isEqualByComparingTo("3000.00");
		assertThat(detail.installments()).hasSize(3);
		assertThat(detail.installments().get(2).dueAmount()).isEqualByComparingTo("1000.00");
		assertThat(summary.outstandingBalance()).isEqualByComparingTo("3000.00");
	}

	@Test
	void fullPaymentCreatesOneInstallmentForTheWholeAmount() {
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.MEMBER, true)));
		when(planRepository.findById(1L)).thenReturn(Optional.of(plan(true)));
		when(subscriptionRepository.existsOverlapping(eq(5L), any(), any(), eq(SubscriptionStatus.CANCELLED)))
				.thenReturn(false);
		stubSaves();

		SubscriptionResponse.Detail detail = service.create(
				new CreateSubscriptionRequest(5L, 1L, PaymentOption.FULL, TODAY));

		assertThat(detail.installments()).hasSize(1);
		assertThat(detail.installments().get(0).dueAmount()).isEqualByComparingTo("3000.00");
	}

	@Test
	void futureStartIsPendingUntilItBegins() {
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.MEMBER, true)));
		when(planRepository.findById(1L)).thenReturn(Optional.of(plan(true)));
		when(subscriptionRepository.existsOverlapping(eq(5L), any(), any(), eq(SubscriptionStatus.CANCELLED)))
				.thenReturn(false);
		stubSaves();

		SubscriptionResponse.Detail detail = service.create(
				new CreateSubscriptionRequest(5L, 1L, PaymentOption.FULL, TODAY.plusDays(5)));

		assertThat(detail.subscription().status()).isEqualTo(SubscriptionStatus.PENDING);
		assertThat(detail.subscription().effectiveStatus()).isEqualTo(SubscriptionStatus.PENDING);
	}

	@Test
	void adminOrStaffAccountCannotBeAssignedASubscription() {
		when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L, Role.STAFF, true)));

		assertThatThrownBy(() -> service.create(new CreateSubscriptionRequest(9L, 1L, PaymentOption.FULL, null)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void inactivePlanCannotBeAssigned() {
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.MEMBER, true)));
		when(planRepository.findById(1L)).thenReturn(Optional.of(plan(false)));

		assertThatThrownBy(() -> service.create(new CreateSubscriptionRequest(5L, 1L, PaymentOption.FULL, null)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void overlappingSubscriptionIsRejectedAndNothingIsSaved() {
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.MEMBER, true)));
		when(planRepository.findById(1L)).thenReturn(Optional.of(plan(true)));
		when(subscriptionRepository.existsOverlapping(eq(5L), any(), any(), eq(SubscriptionStatus.CANCELLED)))
				.thenReturn(true);

		assertThatThrownBy(() -> service.create(new CreateSubscriptionRequest(5L, 1L, PaymentOption.FULL, TODAY)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
		verify(subscriptionRepository, never()).save(any());
		verify(installmentRepository, never()).saveAll(anyList());
	}

	@Test
	void startDateInThePastIsRejected() {
		assertThatThrownBy(() -> service.create(
				new CreateSubscriptionRequest(5L, 1L, PaymentOption.FULL, TODAY.minusDays(1))))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("past");
	}

	// ---------- renewal ----------

	@Test
	void renewalStartsTheDayAfterTheCurrentEndAndKeepsTheOriginal() {
		MemberSubscription original = subscription(7L, SubscriptionStatus.ACTIVE, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 3, 31));
		when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(original));
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.MEMBER, true)));
		when(planRepository.findById(1L)).thenReturn(Optional.of(plan(true)));
		when(subscriptionRepository.existsOverlapping(eq(5L), any(), any(), eq(SubscriptionStatus.CANCELLED)))
				.thenReturn(false);
		stubSaves();

		SubscriptionResponse.Detail renewed = service.renew(7L, new RenewSubscriptionRequest(null,
				PaymentOption.FULL));

		assertThat(renewed.subscription().startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
		assertThat(renewed.subscription().status()).isEqualTo(SubscriptionStatus.PENDING);
		assertThat(original.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(original.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 31));
		ArgumentCaptor<MemberSubscription> saved = ArgumentCaptor.forClass(MemberSubscription.class);
		verify(subscriptionRepository).save(saved.capture());
		assertThat(saved.getValue()).isNotSameAs(original);
	}

	@Test
	void renewingAnExpiredSubscriptionStartsToday() {
		MemberSubscription expired = subscription(7L, SubscriptionStatus.EXPIRED, LocalDate.of(2025, 10, 1),
				LocalDate.of(2025, 12, 31));
		when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(expired));
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.MEMBER, true)));
		when(planRepository.findById(1L)).thenReturn(Optional.of(plan(true)));
		when(subscriptionRepository.existsOverlapping(eq(5L), any(), any(), eq(SubscriptionStatus.CANCELLED)))
				.thenReturn(false);
		stubSaves();

		SubscriptionResponse.Detail renewed = service.renew(7L, new RenewSubscriptionRequest(null,
				PaymentOption.FULL));

		assertThat(renewed.subscription().startDate()).isEqualTo(TODAY);
		assertThat(renewed.subscription().status()).isEqualTo(SubscriptionStatus.ACTIVE);
	}

	@Test
	void cancelledSubscriptionCannotBeRenewed() {
		MemberSubscription cancelled = subscription(7L, SubscriptionStatus.CANCELLED, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 3, 31));
		when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(cancelled));

		assertThatThrownBy(() -> service.renew(7L, new RenewSubscriptionRequest(null, PaymentOption.FULL)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
		verify(subscriptionRepository, never()).save(any());
	}

	// ---------- status and member access ----------

	@Test
	void effectiveStatusIsDerivedFromDatesNotTheStoredValue() {
		assertThat(SubscriptionService.effectiveStatus(
				subscription(1L, SubscriptionStatus.PENDING, TODAY.minusDays(1), TODAY.plusDays(10)), TODAY))
				.isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(SubscriptionService.effectiveStatus(
				subscription(2L, SubscriptionStatus.ACTIVE, TODAY.plusDays(1), TODAY.plusDays(10)), TODAY))
				.isEqualTo(SubscriptionStatus.PENDING);
		assertThat(SubscriptionService.effectiveStatus(
				subscription(3L, SubscriptionStatus.ACTIVE, TODAY.minusDays(30), TODAY.minusDays(1)), TODAY))
				.isEqualTo(SubscriptionStatus.EXPIRED);
		assertThat(SubscriptionService.effectiveStatus(
				subscription(4L, SubscriptionStatus.CANCELLED, TODAY.minusDays(30), TODAY.plusDays(1)), TODAY))
				.isEqualTo(SubscriptionStatus.CANCELLED);
	}

	@Test
	void memberCannotReadAnotherMembersSubscription() {
		assertThatThrownBy(() -> service.currentForMember(5L, new CurrentUser(6L, Role.MEMBER)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
		verify(subscriptionRepository, never()).findByUserIdAndStatusNot(anyLong(), any());
	}

	@Test
	void memberWithNoSubscriptionGetsNotFound() {
		when(subscriptionRepository.findByUserIdAndStatusNot(5L, SubscriptionStatus.CANCELLED)).thenReturn(List.of());

		assertThatThrownBy(() -> service.currentForMember(5L, new CurrentUser(5L, Role.MEMBER)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

}
