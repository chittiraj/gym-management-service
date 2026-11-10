package com.gym.management.service;

import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.SubscriptionInstallment;
import com.gym.management.entity.SubscriptionStatus;
import com.gym.management.repository.MemberSubscriptionRepository;
import com.gym.management.repository.SubscriptionInstallmentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds membership and balance figures for a set of members with two queries, however many members are in the
 * set. Callers use this for directory pages and detail views, so the list endpoint does not query once per member.
 */
@Service
public class MembershipService {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final MemberSubscriptionRepository subscriptionRepository;
	private final SubscriptionInstallmentRepository installmentRepository;
	private final Clock clock;

	public MembershipService(MemberSubscriptionRepository subscriptionRepository,
			SubscriptionInstallmentRepository installmentRepository, Clock clock) {
		this.subscriptionRepository = subscriptionRepository;
		this.installmentRepository = installmentRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Map<Long, MemberFinancials> snapshotsFor(Collection<Long> memberIds) {
		Map<Long, MemberFinancials> result = new HashMap<>();
		if (memberIds.isEmpty()) {
			return result;
		}
		LocalDate today = LocalDate.now(clock);
		List<MemberSubscription> subscriptions =
				subscriptionRepository.findByUserIdInAndStatusNot(memberIds, SubscriptionStatus.CANCELLED);
		Map<Long, List<MemberSubscription>> byMember = subscriptions.stream()
				.collect(Collectors.groupingBy(MemberSubscription::getUserId));
		List<Long> subscriptionIds = subscriptions.stream().map(MemberSubscription::getSubscriptionId).toList();
		Map<Long, List<SubscriptionInstallment>> installmentsBySubscription = subscriptionIds.isEmpty()
				? Map.of()
				: installmentRepository.findBySubscriptionIdInOrderByInstallmentNumberAsc(subscriptionIds).stream()
						.collect(Collectors.groupingBy(SubscriptionInstallment::getSubscriptionId));

		for (Long memberId : memberIds) {
			List<MemberSubscription> memberSubscriptions = byMember.getOrDefault(memberId, List.of());
			if (memberSubscriptions.isEmpty()) {
				result.put(memberId, MemberFinancials.none());
				continue;
			}
			result.put(memberId, build(memberSubscriptions, installmentsBySubscription, today));
		}
		return result;
	}

	private static MemberFinancials build(List<MemberSubscription> subscriptions,
			Map<Long, List<SubscriptionInstallment>> installmentsBySubscription, LocalDate today) {
		MembershipStatus status = MembershipCalculator.statusOf(subscriptions, today);
		MemberSubscription current = currentOf(subscriptions, today).orElse(null);

		BigDecimal outstanding = ZERO;
		for (MemberSubscription subscription : subscriptions) {
			for (SubscriptionInstallment installment : installmentsBySubscription.getOrDefault(
					subscription.getSubscriptionId(), List.of())) {
				outstanding = outstanding.add(InstallmentRules.outstanding(installment.getDueAmount(),
						installment.getPaidAmount()));
			}
		}
		if (current == null) {
			return new MemberFinancials(status, null, outstanding, ZERO, ZERO, 0, null);
		}
		List<SubscriptionInstallment> installments =
				installmentsBySubscription.getOrDefault(current.getSubscriptionId(), List.of());
		BigDecimal received = ZERO;
		BigDecimal monthlyDue = null;
		for (SubscriptionInstallment installment : installments) {
			received = received.add(installment.getPaidAmount());
			if (monthlyDue == null && installment.getOutstandingAmount().signum() > 0) {
				monthlyDue = installment.getDueAmount();
			}
		}
		return new MemberFinancials(status, current, outstanding, current.getTotalAmount().setScale(2), received,
				installments.size(), monthlyDue);
	}

	/** The valid subscription if there is one, otherwise the most recently starting one. */
	static Optional<MemberSubscription> currentOf(List<MemberSubscription> subscriptions, LocalDate today) {
		Optional<MemberSubscription> valid = MembershipCalculator.currentValid(subscriptions, today);
		if (valid.isPresent()) {
			return valid;
		}
		return subscriptions.stream().max(Comparator.comparing(MemberSubscription::getStartDate));
	}

}
