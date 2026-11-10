package com.gym.management.service;

import com.gym.management.dto.InstallmentResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.dto.PaymentRequests.RecordInstallmentPaymentRequest;
import com.gym.management.dto.PaymentResponse;
import com.gym.management.entity.InstallmentStatus;
import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.Role;
import com.gym.management.entity.SubscriptionInstallment;
import com.gym.management.entity.User;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.GymSpecifications;
import com.gym.management.repository.MemberSubscriptionRepository;
import com.gym.management.repository.SubscriptionInstallmentRepository;
import com.gym.management.repository.UserRepository;
import com.gym.management.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Installment reads, plus payments against a single installment (which run through PaymentService). */
@Service
public class InstallmentService {

	private final SubscriptionInstallmentRepository installmentRepository;
	private final MemberSubscriptionRepository subscriptionRepository;
	private final UserRepository userRepository;
	private final PaymentService paymentService;
	private final Clock clock;

	public InstallmentService(SubscriptionInstallmentRepository installmentRepository,
			MemberSubscriptionRepository subscriptionRepository, UserRepository userRepository,
			PaymentService paymentService, Clock clock) {
		this.installmentRepository = installmentRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.userRepository = userRepository;
		this.paymentService = paymentService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<InstallmentResponse> list(InstallmentStatus status, Long subscriptionId, int page, int size) {
		return page(GymSpecifications.installmentFilter(status, subscriptionId, null, false, LocalDate.now(clock)),
				page, size);
	}

	/** Installments that still have money owed, oldest due date first. */
	@Transactional(readOnly = true)
	public PageResponse<InstallmentResponse> pending(int page, int size) {
		return page(GymSpecifications.installmentFilter(null, null, null, true, LocalDate.now(clock)), page, size);
	}

	/** A member's installments across all of their subscriptions. A MEMBER may read only their own. */
	@Transactional(readOnly = true)
	public PageResponse<InstallmentResponse> forMember(long memberId, CurrentUser caller, int page, int size) {
		caller.requireCanViewMember(memberId);
		userRepository.findByUserIdAndRole(memberId, Role.MEMBER)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found."));
		return page(GymSpecifications.installmentFilter(null, null, memberId, false, LocalDate.now(clock)), page, size);
	}

	@Transactional(readOnly = true)
	public InstallmentResponse get(long installmentId) {
		SubscriptionInstallment installment = installmentRepository.findById(installmentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Installment not found."));
		LocalDate today = LocalDate.now(clock);
		MemberSubscription subscription = subscriptionRepository.findById(installment.getSubscriptionId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found."));
		String name = userRepository.findById(subscription.getUserId()).map(User::getFullName).orElse(null);
		return InstallmentResponse.of(installment, today, subscription.getUserId(), name);
	}

	/** Records a payment against one installment. The payment is allocated only to that installment. */
	@Transactional
	public PaymentResponse pay(long installmentId, RecordInstallmentPaymentRequest request, CurrentUser caller) {
		return paymentService.recordForInstallment(installmentId, request, caller);
	}

	/** Shared paging, with subscription and member lookups loaded once per page. */
	private PageResponse<InstallmentResponse> page(
			org.springframework.data.jpa.domain.Specification<SubscriptionInstallment> spec, int page, int size) {
		LocalDate today = LocalDate.now(clock);
		Page<SubscriptionInstallment> result = installmentRepository.findAll(spec,
				PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "dueDate")
						.and(Sort.by(Sort.Direction.ASC, "installmentId"))));
		List<Long> subscriptionIds = result.getContent().stream().map(SubscriptionInstallment::getSubscriptionId)
				.distinct().toList();
		Map<Long, MemberSubscription> subscriptions = subscriptionIds.isEmpty() ? Map.of()
				: subscriptionRepository.findAllById(subscriptionIds).stream()
						.collect(Collectors.toMap(MemberSubscription::getSubscriptionId, s -> s));
		List<Long> memberIds = subscriptions.values().stream().map(MemberSubscription::getUserId).distinct().toList();
		Map<Long, String> names = memberIds.isEmpty() ? Map.of()
				: userRepository.findAllById(memberIds).stream()
						.collect(Collectors.toMap(User::getUserId, User::getFullName));
		return PageResponse.from(result.map(installment -> {
			MemberSubscription subscription = subscriptions.get(installment.getSubscriptionId());
			Long memberId = subscription == null ? null : subscription.getUserId();
			return InstallmentResponse.of(installment, today, memberId,
					memberId == null ? null : names.get(memberId));
		}));
	}

}
