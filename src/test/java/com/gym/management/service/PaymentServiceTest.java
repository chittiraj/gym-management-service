package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gym.management.dto.InvoiceResponse;
import com.gym.management.dto.PaymentRequests.RecordInstallmentPaymentRequest;
import com.gym.management.dto.PaymentRequests.RecordPaymentRequest;
import com.gym.management.dto.PaymentResponse;
import com.gym.management.entity.InstallmentStatus;
import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.Payment;
import com.gym.management.entity.PaymentAllocation;
import com.gym.management.entity.PaymentMethod;
import com.gym.management.entity.PaymentOption;
import com.gym.management.entity.PaymentStatus;
import com.gym.management.entity.Role;
import com.gym.management.entity.SubscriptionInstallment;
import com.gym.management.entity.SubscriptionStatus;
import com.gym.management.entity.User;
import com.gym.management.exception.ApiException;
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
class PaymentServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 10, 10, 0);
	private static final LocalDate TODAY = LocalDate.of(2026, 2, 10);
	private static final CurrentUser ADMIN = new CurrentUser(1L, Role.ADMIN);

	@Mock
	PaymentRepository paymentRepository;
	@Mock
	PaymentAllocationRepository allocationRepository;
	@Mock
	MemberSubscriptionRepository subscriptionRepository;
	@Mock
	SubscriptionInstallmentRepository installmentRepository;
	@Mock
	UserRepository userRepository;

	private PaymentService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
		service = new PaymentService(paymentRepository, allocationRepository, subscriptionRepository,
				installmentRepository, userRepository, clock);
	}

	// ---------- fixtures ----------

	private static MemberSubscription subscription(SubscriptionStatus status) {
		MemberSubscription subscription = new MemberSubscription(5L, 1L, "QUARTERLY", "Quarterly", 3,
				new BigDecimal("3000.00"), PaymentOption.INSTALLMENT, LocalDate.of(2026, 1, 15),
				LocalDate.of(2026, 4, 14), status, NOW);
		ReflectionTestUtils.setField(subscription, "subscriptionId", 100L);
		return subscription;
	}

	private static SubscriptionInstallment installment(long id, int number, String due, String paid) {
		SubscriptionInstallment installment = new SubscriptionInstallment(100L, number,
				LocalDate.parse(due).withDayOfMonth(1), LocalDate.parse(due), new BigDecimal("1000.00"), NOW);
		ReflectionTestUtils.setField(installment, "installmentId", id);
		BigDecimal paidAmount = new BigDecimal(paid).setScale(2);
		installment.applyAllocation(paidAmount, InstallmentRules.statusFor(new BigDecimal("1000.00"), paidAmount,
				LocalDate.parse(due), TODAY), NOW);
		return installment;
	}

	private static User user(long id, String name) {
		User user = BeanUtils.instantiateClass(User.class);
		ReflectionTestUtils.setField(user, "userId", id);
		ReflectionTestUtils.setField(user, "fullName", name);
		ReflectionTestUtils.setField(user, "email", name.toLowerCase() + "@gmail.com");
		ReflectionTestUtils.setField(user, "role", Role.MEMBER);
		ReflectionTestUtils.setField(user, "active", true);
		return user;
	}

	/** Three installments of 1000: #1 paid, #2 overdue and unpaid, #3 upcoming and unpaid. */
	private List<SubscriptionInstallment> threeInstallments() {
		return List.of(
				installment(201L, 1, "2026-01-15", "1000.00"),
				installment(202L, 2, "2026-02-15", "0.00"),
				installment(203L, 3, "2026-03-15", "0.00"));
	}

	private void stubLockedSubscription(MemberSubscription subscription) {
		when(subscriptionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(subscription));
	}

	private void stubPaymentSave() {
		when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
			Payment payment = invocation.getArgument(0);
			ReflectionTestUtils.setField(payment, "paymentId", 42L);
			return payment;
		});
	}

	private static RecordPaymentRequest request(String amount, String reference) {
		return new RecordPaymentRequest(100L, new BigDecimal(amount), PaymentMethod.UPI, reference, "Part payment",
				null);
	}

	// ---------- recording ----------

	@Test
	void paymentIsAllocatedOldestFirstAndInstallmentsAreUpdated() {
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));
		List<SubscriptionInstallment> installments = threeInstallments();
		when(installmentRepository.findLockedBySubscriptionId(100L)).thenReturn(installments);
		stubPaymentSave();
		when(allocationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "Ravi")));

		PaymentResponse response = service.recordForSubscription(request("1500.00", "TXN-1"), ADMIN);

		assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
		assertThat(response.amount()).isEqualByComparingTo("1500.00");
		assertThat(response.receivedBy()).isEqualTo(1L);
		assertThat(response.allocations()).extracting(PaymentResponse.Allocation::installmentNumber)
				.containsExactly(2, 3);
		assertThat(installments.get(1).getPaidAmount()).isEqualByComparingTo("1000.00");
		assertThat(installments.get(1).getStatus()).isEqualTo(InstallmentStatus.PAID);
		assertThat(installments.get(2).getPaidAmount()).isEqualByComparingTo("500.00");
		assertThat(installments.get(2).getStatus()).isEqualTo(InstallmentStatus.PARTIAL);

		ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
		verify(paymentRepository).save(payment.capture());
		assertThat(payment.getValue().getUserId()).isEqualTo(5L);
		assertThat(payment.getValue().getReceivedBy()).isEqualTo(1L);
	}

	@Test
	void paymentLocksTheSubscriptionBeforeReadingInstallments() {
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));
		when(installmentRepository.findLockedBySubscriptionId(100L)).thenReturn(threeInstallments());
		stubPaymentSave();
		when(allocationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "Ravi")));

		service.recordForSubscription(request("100.00", null), ADMIN);

		var order = org.mockito.Mockito.inOrder(subscriptionRepository, installmentRepository);
		order.verify(subscriptionRepository).findByIdForUpdate(100L);
		order.verify(installmentRepository).findLockedBySubscriptionId(100L);
	}

	@Test
	void overpaymentIsRejectedAndNothingIsSaved() {
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));
		when(installmentRepository.findLockedBySubscriptionId(100L)).thenReturn(threeInstallments());

		assertThatThrownBy(() -> service.recordForSubscription(request("2000.01", null), ADMIN))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		verify(paymentRepository, never()).save(any());
		verify(allocationRepository, never()).saveAll(any());
	}

	@Test
	void duplicateSuccessfulTransactionReferenceIsRejected() {
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));
		when(paymentRepository.existsByTransactionReferenceAndPaymentStatus("TXN-9", PaymentStatus.SUCCESS))
				.thenReturn(true);

		assertThatThrownBy(() -> service.recordForSubscription(request("100.00", "TXN-9"), ADMIN))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
		verify(paymentRepository, never()).save(any());
	}

	@Test
	void cancelledSubscriptionCannotTakePayments() {
		stubLockedSubscription(subscription(SubscriptionStatus.CANCELLED));

		assertThatThrownBy(() -> service.recordForSubscription(request("100.00", null), ADMIN))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
		verify(paymentRepository, never()).save(any());
	}

	@Test
	void fullyPaidSubscriptionCannotTakeAnotherPayment() {
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));
		List<SubscriptionInstallment> paid = List.of(installment(201L, 1, "2026-01-15", "1000.00"));
		when(installmentRepository.findLockedBySubscriptionId(100L)).thenReturn(paid);

		assertThatThrownBy(() -> service.recordForSubscription(request("1.00", null), ADMIN))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void futurePaymentDateIsRejected() {
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));
		RecordPaymentRequest future = new RecordPaymentRequest(100L, new BigDecimal("100.00"), PaymentMethod.CASH,
				null, null, NOW.plusDays(1));

		assertThatThrownBy(() -> service.recordForSubscription(future, ADMIN)).isInstanceOf(ApiException.class);
		verify(paymentRepository, never()).save(any());
	}

	@Test
	void moreThanTwoDecimalPlacesIsRejected() {
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));

		assertThatThrownBy(() -> service.recordForSubscription(request("10.001", null), ADMIN))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("two decimal places");
	}

	@Test
	void zeroAmountIsRejected() {
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));

		assertThatThrownBy(() -> service.recordForSubscription(request("0.00", null), ADMIN))
				.isInstanceOf(ApiException.class);
	}

	// ---------- installment-targeted payments ----------

	@Test
	void paymentAgainstOneInstallmentAllocatesOnlyToThatInstallment() {
		// The service updates the row it loaded under lock, so the lookup and the locked list must share the instance.
		List<SubscriptionInstallment> installments = threeInstallments();
		SubscriptionInstallment target = installments.get(1);
		when(installmentRepository.findById(202L)).thenReturn(Optional.of(target));
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));
		when(installmentRepository.findLockedBySubscriptionId(100L)).thenReturn(installments);
		stubPaymentSave();
		when(allocationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "Ravi")));

		PaymentResponse response = service.recordForInstallment(202L,
				new RecordInstallmentPaymentRequest(new BigDecimal("600.00"), PaymentMethod.CARD, null, null, null),
				ADMIN);

		assertThat(response.allocations()).hasSize(1);
		assertThat(response.allocations().get(0).installmentNumber()).isEqualTo(2);
		assertThat(target.getPaidAmount()).isEqualByComparingTo("600.00");
		// Due 15 February is after today (10 February), so a partly paid installment is PARTIAL, not OVERDUE.
		assertThat(target.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
	}

	@Test
	void installmentPaymentAboveItsOwnBalanceIsRejectedEvenIfTheSubscriptionOwesMore() {
		when(installmentRepository.findById(202L))
				.thenReturn(Optional.of(installment(202L, 2, "2026-02-15", "0.00")));
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));
		when(installmentRepository.findLockedBySubscriptionId(100L)).thenReturn(threeInstallments());

		assertThatThrownBy(() -> service.recordForInstallment(202L,
				new RecordInstallmentPaymentRequest(new BigDecimal("1000.01"), PaymentMethod.CASH, null, null, null),
				ADMIN)).isInstanceOf(ApiException.class);
		verify(paymentRepository, never()).save(any());
	}

	@Test
	void installmentThatIsAlreadyPaidCannotBePaidAgain() {
		when(installmentRepository.findById(201L)).thenReturn(Optional.of(installment(201L, 1, "2026-01-15",
				"1000.00")));
		stubLockedSubscription(subscription(SubscriptionStatus.ACTIVE));
		when(installmentRepository.findLockedBySubscriptionId(100L)).thenReturn(threeInstallments());

		assertThatThrownBy(() -> service.recordForInstallment(201L,
				new RecordInstallmentPaymentRequest(new BigDecimal("10.00"), PaymentMethod.CASH, null, null, null),
				ADMIN)).isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
	}

	// ---------- invoices ----------

	@Test
	void invoiceIsBuiltFromStoredRowsForASuccessfulPayment() {
		Payment payment = new Payment(5L, 100L, new BigDecimal("1000.00"), PaymentMethod.UPI, "TXN-7",
				PaymentStatus.SUCCESS, NOW, 1L, "Thanks", NOW);
		ReflectionTestUtils.setField(payment, "paymentId", 42L);
		when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
		when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE)));
		when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "Ravi")));
		when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "Admin")));
		when(allocationRepository.findByPaymentIdOrderByAllocationIdAsc(42L))
				.thenReturn(List.of(new PaymentAllocation(42L, 201L, new BigDecimal("1000.00"), NOW)));
		when(installmentRepository.findBySubscriptionIdOrderByInstallmentNumberAsc(100L))
				.thenReturn(threeInstallments());

		InvoiceResponse invoice = service.invoice(42L);

		assertThat(invoice.invoiceNumber()).isEqualTo("INV-000042");
		assertThat(invoice.amountPaid()).isEqualByComparingTo("1000.00");
		assertThat(invoice.member().fullName()).isEqualTo("Ravi");
		assertThat(invoice.subscription().planName()).isEqualTo("Quarterly");
		assertThat(invoice.lines()).hasSize(1);
		assertThat(invoice.lines().get(0).installmentNumber()).isEqualTo(1);
		assertThat(invoice.receivedByName()).isEqualTo("Admin");
		assertThat(invoice.outstandingBalanceNow()).isEqualByComparingTo("2000.00");
	}

	@Test
	void invoiceIsNotIssuedForAFailedPayment() {
		Payment failed = new Payment(5L, 100L, new BigDecimal("1000.00"), PaymentMethod.CARD, null,
				PaymentStatus.FAILED, NOW, 1L, null, NOW);
		when(paymentRepository.findById(43L)).thenReturn(Optional.of(failed));

		assertThatThrownBy(() -> service.invoice(43L))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void invoiceNumberIsDerivedFromThePaymentId() {
		assertThat(PaymentService.invoiceNumber(7L)).isEqualTo("INV-000007");
	}

	@Test
	void unknownPaymentIsNotFound() {
		when(paymentRepository.findById(anyLong())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(999L))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void memberCannotReadAnotherMembersPaymentHistory() {
		assertThatThrownBy(() -> service.memberPayments(9L, new CurrentUser(5L, Role.MEMBER), 0, 20))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
		verify(paymentRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
				any(org.springframework.data.domain.Pageable.class));
	}

	@Test
	void reversedDateRangeIsRejected() {
		assertThatThrownBy(() -> service.list(null, null, null, null, LocalDate.of(2026, 3, 1),
				LocalDate.of(2026, 2, 1), 0, 20)).isInstanceOf(ApiException.class);
	}
}
