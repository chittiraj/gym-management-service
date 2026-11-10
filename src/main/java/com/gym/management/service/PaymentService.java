package com.gym.management.service;

import com.gym.management.dto.InvoiceResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.dto.PaymentRequests.RecordInstallmentPaymentRequest;
import com.gym.management.dto.PaymentRequests.RecordPaymentRequest;
import com.gym.management.dto.PaymentResponse;
import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.Payment;
import com.gym.management.entity.PaymentAllocation;
import com.gym.management.entity.PaymentMethod;
import com.gym.management.entity.PaymentStatus;
import com.gym.management.entity.Role;
import com.gym.management.entity.SubscriptionInstallment;
import com.gym.management.entity.SubscriptionStatus;
import com.gym.management.entity.User;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.GymSpecifications;
import com.gym.management.repository.MemberSubscriptionRepository;
import com.gym.management.repository.PaymentAllocationRepository;
import com.gym.management.repository.PaymentRepository;
import com.gym.management.repository.SubscriptionInstallmentRepository;
import com.gym.management.repository.UserRepository;
import com.gym.management.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 * Records payments and allocates them to installments. Only SUCCESS payments count as collections.
 *
 * <p>Concurrency: every write takes the subscription row lock first and then the subscription's installment rows,
 * always in that order. Concurrent payments for one subscription therefore run one after another, and the balance
 * checks see the committed result of the previous payment. Duplicate transaction references are checked under the
 * same lock. The schema has no unique key on transaction_reference, so a duplicate reference used on two different
 * subscriptions at the same moment is not prevented by the database.
 */
@Service
public class PaymentService {

	private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

	private final PaymentRepository paymentRepository;
	private final PaymentAllocationRepository allocationRepository;
	private final MemberSubscriptionRepository subscriptionRepository;
	private final SubscriptionInstallmentRepository installmentRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public PaymentService(PaymentRepository paymentRepository, PaymentAllocationRepository allocationRepository,
			MemberSubscriptionRepository subscriptionRepository,
			SubscriptionInstallmentRepository installmentRepository, UserRepository userRepository, Clock clock) {
		this.paymentRepository = paymentRepository;
		this.allocationRepository = allocationRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.installmentRepository = installmentRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	@Transactional
	public PaymentResponse recordForSubscription(RecordPaymentRequest request, CurrentUser caller) {
		return record(request.subscriptionId(), null, request.amount(), request.paymentMethod(),
				request.transactionReference(), request.remarks(), request.paymentDate(), caller);
	}

	@Transactional
	public PaymentResponse recordForInstallment(long installmentId, RecordInstallmentPaymentRequest request,
			CurrentUser caller) {
		SubscriptionInstallment installment = installmentRepository.findById(installmentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Installment not found."));
		return record(installment.getSubscriptionId(), installmentId, request.amount(), request.paymentMethod(),
				request.transactionReference(), request.remarks(), request.paymentDate(), caller);
	}

	private PaymentResponse record(long subscriptionId, Long installmentId, BigDecimal requestedAmount,
			PaymentMethod method, String reference, String remarks, LocalDateTime requestedDate, CurrentUser caller) {
		LocalDate today = LocalDate.now(clock);
		LocalDateTime now = LocalDateTime.now(clock);

		MemberSubscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found."));
		if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
			throw new ApiException(HttpStatus.CONFLICT, "Payments cannot be recorded against a cancelled subscription.");
		}
		BigDecimal amount = validatedAmount(requestedAmount);
		LocalDateTime paymentDate = requestedDate == null ? now : requestedDate;
		if (paymentDate.isAfter(now)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Payment date cannot be in the future.");
		}
		String transactionReference = blankToNull(reference);
		if (transactionReference != null
				&& paymentRepository.existsByTransactionReferenceAndPaymentStatus(transactionReference,
						PaymentStatus.SUCCESS)) {
			throw new ApiException(HttpStatus.CONFLICT,
					"A successful payment with this transaction reference already exists.");
		}

		List<SubscriptionInstallment> installments = installmentRepository.findLockedBySubscriptionId(subscriptionId);
		Map<Long, SubscriptionInstallment> byId = new HashMap<>();
		installments.forEach(installment -> byId.put(installment.getInstallmentId(), installment));

		List<PaymentAllocator.Open> open;
		if (installmentId != null) {
			SubscriptionInstallment target = byId.get(installmentId);
			if (target == null) {
				throw new ApiException(HttpStatus.NOT_FOUND, "Installment not found.");
			}
			BigDecimal owed = target.getOutstandingAmount();
			if (owed.signum() <= 0) {
				throw new ApiException(HttpStatus.CONFLICT, "This installment is already fully paid.");
			}
			if (amount.compareTo(owed) > 0) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"Payment exceeds the installment's outstanding amount of " + owed.toPlainString() + ".");
			}
			open = List.of(new PaymentAllocator.Open(installmentId, owed));
		} else {
			open = installments.stream()
					.filter(installment -> installment.getOutstandingAmount().signum() > 0)
					.map(installment -> new PaymentAllocator.Open(installment.getInstallmentId(),
							installment.getOutstandingAmount()))
					.toList();
			BigDecimal totalOwed = open.stream().map(PaymentAllocator.Open::outstanding)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			if (totalOwed.signum() == 0) {
				throw new ApiException(HttpStatus.CONFLICT, "Nothing is owed on this subscription.");
			}
			if (amount.compareTo(totalOwed) > 0) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"Payment exceeds the outstanding balance of " + totalOwed.toPlainString() + ".");
			}
		}

		List<PaymentAllocator.Share> shares = allocate(amount, open);
		Payment payment = paymentRepository.save(new Payment(subscription.getUserId(), subscriptionId, amount, method,
				transactionReference, PaymentStatus.SUCCESS, paymentDate, caller.userId(), blankToNull(remarks), now));

		List<PaymentAllocation> allocations = new ArrayList<>();
		for (PaymentAllocator.Share share : shares) {
			SubscriptionInstallment installment = byId.get(share.installmentId());
			BigDecimal newPaid = installment.getPaidAmount().add(share.amount());
			installment.applyAllocation(newPaid,
					InstallmentRules.statusFor(installment.getDueAmount(), newPaid, installment.getDueDate(), today),
					now);
			allocations.add(new PaymentAllocation(payment.getPaymentId(), share.installmentId(), share.amount(), now));
		}
		allocationRepository.saveAll(allocations);

		String memberName = nameOf(subscription.getUserId());
		List<PaymentResponse.Allocation> lines = allocations.stream()
				.map(allocation -> new PaymentResponse.Allocation(allocation.getInstallmentId(),
						byId.get(allocation.getInstallmentId()).getInstallmentNumber(),
						allocation.getAllocatedAmount()))
				.toList();
		return toResponse(payment, memberName, lines);
	}

	@Transactional(readOnly = true)
	public PaymentResponse get(long paymentId) {
		Payment payment = findPayment(paymentId);
		return toResponses(List.of(payment)).get(0);
	}

	@Transactional(readOnly = true)
	public PageResponse<PaymentResponse> list(PaymentStatus status, PaymentMethod method, Long userId,
			Long subscriptionId, LocalDate from, LocalDate to, int page, int size) {
		LocalDateTime start = from == null ? null : from.atStartOfDay();
		LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
		if (from != null && to != null && from.isAfter(to)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "The 'from' date must be on or before the 'to' date.");
		}
		Page<Payment> result = paymentRepository.findAll(
				GymSpecifications.paymentFilter(status, method, userId, subscriptionId, start, end),
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paymentDate")
						.and(Sort.by(Sort.Direction.DESC, "paymentId"))));
		List<PaymentResponse> responses = toResponses(result.getContent());
		Map<Long, PaymentResponse> byId = new HashMap<>();
		responses.forEach(response -> byId.put(response.paymentId(), response));
		return PageResponse.from(result.map(payment -> byId.get(payment.getPaymentId())));
	}

	/** A member's payment history. A MEMBER may read only their own history. */
	@Transactional(readOnly = true)
	public PageResponse<PaymentResponse> memberPayments(long memberId, CurrentUser caller, int page, int size) {
		caller.requireCanViewMember(memberId);
		requireMember(memberId);
		return list(null, null, memberId, null, null, null, page, size);
	}

	/** Receipt for a successful payment. Failed or refunded payments do not produce an invoice. */
	@Transactional(readOnly = true)
	public InvoiceResponse invoice(long paymentId) {
		Payment payment = findPayment(paymentId);
		if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
			throw new ApiException(HttpStatus.CONFLICT, "Invoices are issued only for successful payments.");
		}
		MemberSubscription subscription = subscriptionRepository.findById(payment.getSubscriptionId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found."));
		User member = userRepository.findById(payment.getUserId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found."));
		List<SubscriptionInstallment> installments =
				installmentRepository.findBySubscriptionIdOrderByInstallmentNumberAsc(subscription.getSubscriptionId());
		Map<Long, SubscriptionInstallment> byId = new HashMap<>();
		installments.forEach(installment -> byId.put(installment.getInstallmentId(), installment));

		List<InvoiceResponse.Line> lines = allocationRepository.findByPaymentIdOrderByAllocationIdAsc(paymentId).stream()
				.map(allocation -> {
					SubscriptionInstallment installment = byId.get(allocation.getInstallmentId());
					return new InvoiceResponse.Line(installment.getInstallmentNumber(), installment.getDueDate(),
							allocation.getAllocatedAmount());
				})
				.sorted(Comparator.comparing(InvoiceResponse.Line::installmentNumber))
				.toList();
		BigDecimal outstanding = installments.stream()
				.map(SubscriptionInstallment::getOutstandingAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return new InvoiceResponse(
				invoiceNumber(paymentId),
				payment.getPaymentId(),
				payment.getPaymentDate(),
				payment.getAmount(),
				payment.getPaymentMethod(),
				payment.getTransactionReference(),
				payment.getRemarks(),
				payment.getReceivedBy() == null ? null : nameOf(payment.getReceivedBy()),
				new InvoiceResponse.Member(member.getUserId(), member.getFullName(), member.getEmail(),
						member.getPhone()),
				new InvoiceResponse.Subscription(subscription.getSubscriptionId(), subscription.getPlanCodeSnapshot(),
						subscription.getPlanNameSnapshot(), subscription.getDurationMonths(),
						subscription.getTotalAmount(), subscription.getPaymentOption(), subscription.getStartDate(),
						subscription.getEndDate()),
				lines,
				outstanding);
	}

	// ---------- helpers ----------

	/** Batch-loads allocations, installment numbers and member names for a page of payments. */
	List<PaymentResponse> toResponses(List<Payment> payments) {
		if (payments.isEmpty()) {
			return List.of();
		}
		List<Long> paymentIds = payments.stream().map(Payment::getPaymentId).toList();
		List<PaymentAllocation> allocations = allocationRepository.findByPaymentIdIn(paymentIds);
		Map<Long, Integer> installmentNumbers = installmentRepository
				.findAllById(allocations.stream().map(PaymentAllocation::getInstallmentId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(SubscriptionInstallment::getInstallmentId,
						SubscriptionInstallment::getInstallmentNumber));
		Map<Long, List<PaymentAllocation>> byPayment = allocations.stream()
				.collect(Collectors.groupingBy(PaymentAllocation::getPaymentId));
		Map<Long, String> names = userRepository.findAllById(payments.stream().map(Payment::getUserId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(User::getUserId, User::getFullName));
		return payments.stream()
				.map(payment -> toResponse(payment, names.get(payment.getUserId()),
						byPayment.getOrDefault(payment.getPaymentId(), List.of()).stream()
								.map(allocation -> new PaymentResponse.Allocation(allocation.getInstallmentId(),
										installmentNumbers.getOrDefault(allocation.getInstallmentId(), 0),
										allocation.getAllocatedAmount()))
								.toList()))
				.toList();
	}

	private static PaymentResponse toResponse(Payment payment, String memberName,
			List<PaymentResponse.Allocation> allocations) {
		return new PaymentResponse(
				payment.getPaymentId(),
				payment.getUserId(),
				memberName,
				payment.getSubscriptionId(),
				payment.getAmount(),
				payment.getPaymentMethod(),
				payment.getTransactionReference(),
				payment.getPaymentStatus(),
				payment.getPaymentDate(),
				payment.getReceivedBy(),
				payment.getRemarks(),
				allocations);
	}

	private Payment findPayment(long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found."));
	}

	private void requireMember(long memberId) {
		userRepository.findByUserIdAndRole(memberId, Role.MEMBER)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found."));
	}

	private String nameOf(Long userId) {
		return userRepository.findById(userId).map(User::getFullName).orElse(null);
	}

	/** Validates a payment amount: positive, at most two decimal places, and within the column range. */
	static BigDecimal validatedAmount(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Payment amount must be greater than zero.");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Payment amount can have at most two decimal places.");
		}
		if (amount.compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Payment amount is too large.");
		}
		return amount.setScale(2);
	}

	private static List<PaymentAllocator.Share> allocate(BigDecimal amount, List<PaymentAllocator.Open> open) {
		try {
			return PaymentAllocator.allocate(amount, open);
		} catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	/** Derived from the payment id. Not stored; the same payment always gets the same number. */
	static String invoiceNumber(long paymentId) {
		return String.format("INV-%06d", paymentId);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
