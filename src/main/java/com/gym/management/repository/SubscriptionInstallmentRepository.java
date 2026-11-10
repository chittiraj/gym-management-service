package com.gym.management.repository;

import com.gym.management.entity.SubscriptionInstallment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionInstallmentRepository extends JpaRepository<SubscriptionInstallment, Long>,
		JpaSpecificationExecutor<SubscriptionInstallment> {

	List<SubscriptionInstallment> findBySubscriptionIdOrderByInstallmentNumberAsc(Long subscriptionId);

	/** Installments for many subscriptions in one query, used when building member and subscription views. */
	List<SubscriptionInstallment> findBySubscriptionIdInOrderByInstallmentNumberAsc(Collection<Long> subscriptionIds);

	/** Locks every installment of a subscription in installment order. Taken after the subscription row lock. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from SubscriptionInstallment i where i.subscriptionId = :sid order by i.installmentNumber")
	List<SubscriptionInstallment> findLockedBySubscriptionId(@Param("sid") Long subscriptionId);

	/** Locks an installment's row. Used when a payment targets one installment. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from SubscriptionInstallment i where i.installmentId = :id")
	Optional<SubscriptionInstallment> findByIdForUpdate(@Param("id") Long id);

}
