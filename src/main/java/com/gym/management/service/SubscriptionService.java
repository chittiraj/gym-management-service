package com.gym.management.service;

import com.gym.management.dto.InstallmentResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.dto.SubscriptionRequests.CreateSubscriptionRequest;
import com.gym.management.dto.SubscriptionRequests.RenewSubscriptionRequest;
import com.gym.management.dto.SubscriptionResponse;
import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.PaymentOption;
import com.gym.management.entity.Role;
import com.gym.management.entity.SubscriptionInstallment;
import com.gym.management.entity.SubscriptionPlan;
import com.gym.management.entity.SubscriptionStatus;
import com.gym.management.entity.User;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.GymSpecifications;
import com.gym.management.repository.MemberSubscriptionRepository;
import com.gym.management.repository.SubscriptionInstallmentRepository;
import com.gym.management.repository.SubscriptionPlanRepository;
import com.gym.management.repository.UserRepository;
import com.gym.management.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member subscriptions. Overlapping subscriptions are rejected, and a renewal creates a new row so history is
 * kept. Amounts come from the plan at the time of assignment and are stored as snapshots.
 */
@Service
public class SubscriptionService {

	private static final int MAX_START_YEARS_AHEAD = 1;

	private final MemberSubscriptionRepository subscriptionRepository;
	private final SubscriptionInstallmentRepository installmentRepository;
	private final SubscriptionPlanRepository planRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public SubscriptionService(MemberSubscriptionRepository subscriptionRepository,
			SubscriptionInstallmentRepository installmentRepository, SubscriptionPlanRepository planRepository,
			UserRepository userRepository, Clock clock) {
		this.subscriptionRepository = subscriptionRepository;
		this.installmentRepository = installmentRepository;
		this.planRepository = planRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	@Transactional
	public SubscriptionResponse.Detail create(CreateSubscriptionRequest request) {
		LocalDate today = LocalDate.now(clock);
		// Validate the request before any lookups, so bad input is reported without touching the database.
		LocalDate start = request.startDate() == null ? today : request.startDate();
		validateStart(start, today);
		User member = assignableMember(request.userId());
		SubscriptionPlan plan = activePlan(request.planId());
		return createFor(member, plan, request.paymentOption(), start, today);
	}

	/**
	 * Creates the next subscription after an existing one. Starts the day after the current end date, or today if
	 * that date has passed. The original subscription is left unchanged.
	 */
	@Transactional
	public SubscriptionResponse.Detail renew(long subscriptionId, RenewSubscriptionRequest request) {
		LocalDate today = LocalDate.now(clock);
		MemberSubscription original = findSubscription(subscriptionId);
		if (original.getStatus() == SubscriptionStatus.CANCELLED) {
			throw new ApiException(HttpStatus.CONFLICT, "A cancelled subscription cannot be renewed.");
		}
		User member = assignableMember(original.getUserId());
		SubscriptionPlan plan = activePlan(request.planId() != null ? request.planId() : original.getPlanId());
		LocalDate start = original.getEndDate().isBefore(today) ? today : original.getEndDate().plusDays(1);
		return createFor(member, plan, request.paymentOption(), start, today);
	}

	@Transactional(readOnly = true)
	public SubscriptionResponse.Detail get(long subscriptionId) {
		return detail(findSubscription(subscriptionId), LocalDate.now(clock));
	}

	@Transactional(readOnly = true)
	public PageResponse<SubscriptionResponse> list(SubscriptionStatus status, Long userId, int page, int size) {
		LocalDate today = LocalDate.now(clock);
		Page<MemberSubscription> result = subscriptionRepository.findAll(
				GymSpecifications.subscriptionFilter(status, userId),
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDate")
						.and(Sort.by(Sort.Direction.DESC, "subscriptionId"))));
		return PageResponse.from(summaryPage(result, today));
	}

	/** The member's current subscription (valid today, else the most recent). 404 if the member has none. */
	@Transactional(readOnly = true)
	public SubscriptionResponse.Detail currentForMember(long memberId, CurrentUser caller) {
		caller.requireCanViewMember(memberId);
		LocalDate today = LocalDate.now(clock);
		List<MemberSubscription> subscriptions =
				subscriptionRepository.findByUserIdAndStatusNot(memberId, SubscriptionStatus.CANCELLED);
		MemberSubscription current = MembershipService.currentOf(subscriptions, today)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This member has no subscription."));
		return detail(current, today);
	}

	@Transactional(readOnly = true)
	public PageResponse<SubscriptionResponse> expiring(int days, int page, int size) {
		LocalDate today = LocalDate.now(clock);
		Page<MemberSubscription> result = subscriptionRepository.findAll(
				GymSpecifications.expiringBetween(today, today.plusDays(days)),
				PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "endDate")
						.and(Sort.by(Sort.Direction.ASC, "subscriptionId"))));
		return PageResponse.from(summaryPage(result, today));
	}

	@Transactional(readOnly = true)
	public PageResponse<SubscriptionResponse> expired(int page, int size) {
		LocalDate today = LocalDate.now(clock);
		Page<MemberSubscription> result = subscriptionRepository.findAll(
				GymSpecifications.endedBefore(today),
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "endDate")
						.and(Sort.by(Sort.Direction.DESC, "subscriptionId"))));
		return PageResponse.from(summaryPage(result, today));
	}

	// ---------- shared logic ----------

	private SubscriptionResponse.Detail createFor(User member, SubscriptionPlan plan, PaymentOption option,
			LocalDate start, LocalDate today) {
		LocalDate end = start.plusMonths(plan.getDurationMonths()).minusDays(1);
		if (subscriptionRepository.existsOverlapping(member.getUserId(), start, end, SubscriptionStatus.CANCELLED)) {
			throw new ApiException(HttpStatus.CONFLICT,
					"The member already has a subscription that overlaps these dates.");
		}
		List<InstallmentRules.Draft> drafts = schedule(plan.getPrice(), plan.getDurationMonths(), option, start);
		LocalDateTime now = LocalDateTime.now(clock);
		SubscriptionStatus status = start.isAfter(today) ? SubscriptionStatus.PENDING : SubscriptionStatus.ACTIVE;
		MemberSubscription subscription = subscriptionRepository.save(new MemberSubscription(
				member.getUserId(), plan.getPlanId(), plan.getPlanCode(), plan.getPlanName(),
				plan.getDurationMonths(), plan.getPrice().setScale(2), option, start, end, status, now));
		List<SubscriptionInstallment> installments = installmentRepository.saveAll(drafts.stream()
				.map(draft -> new SubscriptionInstallment(subscription.getSubscriptionId(), draft.installmentNumber(),
						draft.billingMonth(), draft.dueDate(), draft.dueAmount(), now))
				.toList());
		return new SubscriptionResponse.Detail(
				summary(subscription, member.getFullName(), installments, today),
				installments.stream().map(i -> InstallmentResponse.of(i, today)).toList());
	}

	private static List<InstallmentRules.Draft> schedule(BigDecimal price, int durationMonths, PaymentOption option,
			LocalDate start) {
		try {
			return InstallmentRules.schedule(price, durationMonths, option, start);
		} catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	private User assignableMember(long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found."));
		if (user.getRole() != Role.MEMBER) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Subscriptions can only be assigned to member accounts.");
		}
		if (!user.isActive()) {
			throw new ApiException(HttpStatus.CONFLICT, "The member account is inactive.");
		}
		return user;
	}

	private SubscriptionPlan activePlan(long planId) {
		SubscriptionPlan plan = planRepository.findById(planId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));
		if (!plan.isActive()) {
			throw new ApiException(HttpStatus.CONFLICT, "The plan is not active.");
		}
		return plan;
	}

	private static void validateStart(LocalDate start, LocalDate today) {
		if (start.isBefore(today)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Start date cannot be in the past.");
		}
		if (start.isAfter(today.plusYears(MAX_START_YEARS_AHEAD))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Start date cannot be more than a year ahead.");
		}
	}

	private MemberSubscription findSubscription(long subscriptionId) {
		return subscriptionRepository.findById(subscriptionId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found."));
	}

	private SubscriptionResponse.Detail detail(MemberSubscription subscription, LocalDate today) {
		List<SubscriptionInstallment> installments =
				installmentRepository.findBySubscriptionIdOrderByInstallmentNumberAsc(subscription.getSubscriptionId());
		String name = userRepository.findById(subscription.getUserId()).map(User::getFullName).orElse(null);
		return new SubscriptionResponse.Detail(
				summary(subscription, name, installments, today),
				installments.stream().map(i -> InstallmentResponse.of(i, today)).toList());
	}

	/** A page of summaries. Installments and member names are each loaded in one query for the whole page. */
	private Page<SubscriptionResponse> summaryPage(Page<MemberSubscription> page, LocalDate today) {
		List<MemberSubscription> subscriptions = page.getContent();
		if (subscriptions.isEmpty()) {
			return page.map(subscription -> null);
		}
		List<Long> subscriptionIds = subscriptions.stream().map(MemberSubscription::getSubscriptionId).toList();
		Map<Long, List<SubscriptionInstallment>> installments = installmentRepository
				.findBySubscriptionIdInOrderByInstallmentNumberAsc(subscriptionIds).stream()
				.collect(Collectors.groupingBy(SubscriptionInstallment::getSubscriptionId));
		Map<Long, String> names = userRepository.findAllById(subscriptions.stream()
						.map(MemberSubscription::getUserId).distinct().toList()).stream()
				.collect(Collectors.toMap(User::getUserId, User::getFullName));
		return page.map(subscription -> summary(subscription, names.get(subscription.getUserId()),
				installments.getOrDefault(subscription.getSubscriptionId(), List.of()), today));
	}

	static SubscriptionResponse summary(MemberSubscription subscription, String memberName,
			Collection<SubscriptionInstallment> installments, LocalDate today) {
		BigDecimal paid = BigDecimal.ZERO.setScale(2);
		BigDecimal outstanding = BigDecimal.ZERO.setScale(2);
		for (SubscriptionInstallment installment : installments) {
			paid = paid.add(installment.getPaidAmount());
			outstanding = outstanding.add(InstallmentRules.outstanding(installment.getDueAmount(),
					installment.getPaidAmount()));
		}
		return new SubscriptionResponse(
				subscription.getSubscriptionId(),
				subscription.getUserId(),
				memberName,
				subscription.getPlanId(),
				subscription.getPlanCodeSnapshot(),
				subscription.getPlanNameSnapshot(),
				subscription.getDurationMonths(),
				subscription.getTotalAmount(),
				subscription.getPaymentOption(),
				subscription.getStartDate(),
				subscription.getEndDate(),
				subscription.getStatus(),
				effectiveStatus(subscription, today),
				paid,
				outstanding);
	}

	/** Date-based status. The stored status is kept as recorded; CANCELLED is the only stored status that overrides dates. */
	static SubscriptionStatus effectiveStatus(MemberSubscription subscription, LocalDate today) {
		if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
			return SubscriptionStatus.CANCELLED;
		}
		if (subscription.getStartDate().isAfter(today)) {
			return SubscriptionStatus.PENDING;
		}
		if (subscription.getEndDate().isBefore(today)) {
			return SubscriptionStatus.EXPIRED;
		}
		return SubscriptionStatus.ACTIVE;
	}

}
