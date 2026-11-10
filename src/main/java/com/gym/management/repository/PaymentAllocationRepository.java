package com.gym.management.repository;

import com.gym.management.entity.PaymentAllocation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

	List<PaymentAllocation> findByPaymentIdOrderByAllocationIdAsc(Long paymentId);

	List<PaymentAllocation> findByPaymentIdIn(Collection<Long> paymentIds);

}
