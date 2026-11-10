package com.gym.management.service;

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
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard figures from actual records. Collections come only from payments.amount with status SUCCESS, so failed,
 * refunded and pending payments are excluded and allocations are never added on top. Dates use the server's clock
 * and time zone, and payment_date is compared as local date-time.
 */
@Service
public class DashboardService {

	static final int MIN_YEAR = 2000;
	static final int MAX_YEAR = 2100;

	private final UserRepository userRepository;
	private final MemberSubscriptionRepository subscriptionRepository;
	private final PaymentRepository paymentRepository;
	private final Clock clock;

	public DashboardService(UserRepository userRepository, MemberSubscriptionRepository subscriptionRepository,
			PaymentRepository paymentRepository, Clock clock) {
		this.userRepository = userRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.paymentRepository = paymentRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public DashboardSummaryResponse summary() {
		LocalDate today = LocalDate.now(clock);
		LocalDate cutoff = today.plusDays(MembershipCalculator.EXPIRING_WINDOW_DAYS);
		YearMonth thisMonth = YearMonth.from(today);
		return new DashboardSummaryResponse(
				userRepository.countByRole(Role.MEMBER),
				subscriptionRepository.countActiveMembers(today, LIVE),
				subscriptionRepository.countExpiringMembers(today, cutoff, LIVE),
				paymentRepository.sumAmountBetween(PaymentStatus.SUCCESS, thisMonth.atDay(1).atStartOfDay(),
						thisMonth.plusMonths(1).atDay(1).atStartOfDay()),
				paymentRepository.sumAmountByStatus(PaymentStatus.SUCCESS));
	}

	/** Successful collections for each of the twelve months of the year, January first. */
	@Transactional(readOnly = true)
	public MonthlyCollectionsResponse monthlyCollections(int year) {
		if (year < MIN_YEAR || year > MAX_YEAR) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Year must be between " + MIN_YEAR + " and " + MAX_YEAR + ".");
		}
		List<MonthlyCollectionsResponse.Month> months = new ArrayList<>(12);
		BigDecimal total = BigDecimal.ZERO.setScale(2);
		for (int number = 1; number <= 12; number++) {
			LocalDateTime from = LocalDateTime.of(year, number, 1, 0, 0);
			BigDecimal collected = paymentRepository.sumAmountBetween(PaymentStatus.SUCCESS, from,
					from.plusMonths(1));
			total = total.add(collected);
			months.add(new MonthlyCollectionsResponse.Month(number,
					Month.of(number).getDisplayName(TextStyle.FULL, Locale.ENGLISH), collected));
		}
		return new MonthlyCollectionsResponse(year, total, months);
	}

	/** Subscription statuses that count as live. The dates decide validity. */
	private static final List<SubscriptionStatus> LIVE = List.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE);

}
