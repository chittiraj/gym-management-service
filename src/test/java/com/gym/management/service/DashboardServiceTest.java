package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gym.management.dto.DashboardSummaryResponse;
import com.gym.management.dto.MonthlyCollectionsResponse;
import com.gym.management.entity.PaymentStatus;
import com.gym.management.entity.Role;
import com.gym.management.entity.SubscriptionStatus;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.MemberSubscriptionRepository;
import com.gym.management.repository.PaymentRepository;
import com.gym.management.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

	/** 10 February 2026, so the current month is February and the expiry window ends 17 February. */
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 10, 10, 0);

	@Mock
	UserRepository userRepository;
	@Mock
	MemberSubscriptionRepository subscriptionRepository;
	@Mock
	PaymentRepository paymentRepository;

	private DashboardService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
		service = new DashboardService(userRepository, subscriptionRepository, paymentRepository, clock);
	}

	@Test
	void summaryUsesRealCountsAndSuccessfulPaymentsOnly() {
		LocalDate today = LocalDate.of(2026, 2, 10);
		when(userRepository.countByRole(Role.MEMBER)).thenReturn(40L);
		when(subscriptionRepository.countActiveMembers(eq(today), any())).thenReturn(25L);
		when(subscriptionRepository.countExpiringMembers(eq(today), eq(LocalDate.of(2026, 2, 17)), any()))
				.thenReturn(4L);
		when(paymentRepository.sumAmountBetween(eq(PaymentStatus.SUCCESS), eq(LocalDateTime.of(2026, 2, 1, 0, 0)),
				eq(LocalDateTime.of(2026, 3, 1, 0, 0)))).thenReturn(new BigDecimal("45280.00"));
		when(paymentRepository.sumAmountByStatus(PaymentStatus.SUCCESS)).thenReturn(new BigDecimal("384000.00"));

		DashboardSummaryResponse summary = service.summary();

		assertThat(summary.totalMembers()).isEqualTo(40L);
		assertThat(summary.activeMembers()).isEqualTo(25L);
		assertThat(summary.expiringMemberships()).isEqualTo(4L);
		assertThat(summary.monthlyRevenue()).isEqualByComparingTo("45280.00");
		assertThat(summary.totalCollections()).isEqualByComparingTo("384000.00");
	}

	@Test
	void activeCountsExcludeCancelledAndNeverMatchPendingFutureSubscriptionsByStatusAlone() {
		when(userRepository.countByRole(Role.MEMBER)).thenReturn(0L);
		when(subscriptionRepository.countActiveMembers(any(), any())).thenReturn(0L);
		when(subscriptionRepository.countExpiringMembers(any(), any(), any())).thenReturn(0L);
		when(paymentRepository.sumAmountBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
		when(paymentRepository.sumAmountByStatus(any())).thenReturn(BigDecimal.ZERO);

		service.summary();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<SubscriptionStatus>> live = ArgumentCaptor.forClass(List.class);
		verify(subscriptionRepository).countActiveMembers(any(), live.capture());
		assertThat(live.getValue()).containsExactlyInAnyOrder(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE)
				.doesNotContain(SubscriptionStatus.CANCELLED, SubscriptionStatus.EXPIRED);
	}

	@Test
	void monthlyCollectionsReturnsAllTwelveMonthsWithZeros() {
		when(paymentRepository.sumAmountBetween(eq(PaymentStatus.SUCCESS), any(), any())).thenReturn(
				BigDecimal.ZERO);
		when(paymentRepository.sumAmountBetween(eq(PaymentStatus.SUCCESS), eq(LocalDateTime.of(2026, 3, 1, 0, 0)),
				eq(LocalDateTime.of(2026, 4, 1, 0, 0)))).thenReturn(new BigDecimal("1200.00"));

		MonthlyCollectionsResponse response = service.monthlyCollections(2026);

		assertThat(response.year()).isEqualTo(2026);
		assertThat(response.months()).hasSize(12);
		assertThat(response.months().get(0).monthName()).isEqualTo("January");
		assertThat(response.months().get(0).collectedAmount()).isEqualByComparingTo("0");
		assertThat(response.months().get(2).month()).isEqualTo(3);
		assertThat(response.months().get(2).collectedAmount()).isEqualByComparingTo("1200.00");
		assertThat(response.totalCollected()).isEqualByComparingTo("1200.00");
	}

	@Test
	void yearOutsideTheSupportedRangeIsBadRequest() {
		assertThatThrownBy(() -> service.monthlyCollections(1999))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		verify(paymentRepository, org.mockito.Mockito.never()).sumAmountBetween(any(), any(), any());
	}

	@Test
	void monthlyCollectionsOnlyCountSuccessfulPayments() {
		when(paymentRepository.sumAmountBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);

		service.monthlyCollections(anyYear());

		verify(paymentRepository, org.mockito.Mockito.times(12)).sumAmountBetween(eq(PaymentStatus.SUCCESS), any(),
				any());
	}

	private static int anyYear() {
		return 2026;
	}

}
