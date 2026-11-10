package com.gym.management.repository;

import com.gym.management.entity.Payment;
import com.gym.management.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

	/** True when a successful payment already uses this reference. The schema has no unique key, so the service checks. */
	boolean existsByTransactionReferenceAndPaymentStatus(String transactionReference, PaymentStatus status);

	Page<Payment> findByUserId(Long userId, Pageable pageable);

	/**
	 * Sum of successful payments in a half-open date range [from, to). Only payments.amount is summed, never
	 * allocations, so a payment is counted once. Returns 0.00 when nothing matches.
	 */
	@Query("""
			select coalesce(sum(p.amount), 0) from Payment p
			where p.paymentStatus = :status and p.paymentDate >= :from and p.paymentDate < :to
			""")
	BigDecimal sumAmountBetween(@Param("status") PaymentStatus status, @Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to);

	/** Sum of all payments.amount with the given status, for example successful collections to date. */
	@Query("select coalesce(sum(p.amount), 0) from Payment p where p.paymentStatus = :status")
	BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

}
