package com.gym.management.repository;

import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberSubscriptionRepository extends JpaRepository<MemberSubscription, Long>,
		JpaSpecificationExecutor<MemberSubscription> {

	/**
	 * Locks the subscription row. Every payment for a subscription takes this lock first, so concurrent payments
	 * are applied one after another and cannot overpay an installment.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from MemberSubscription s where s.subscriptionId = :id")
	Optional<MemberSubscription> findByIdForUpdate(@Param("id") Long id);

	/** Non-cancelled subscriptions for a set of members, loaded in one query for a page of members. */
	List<MemberSubscription> findByUserIdInAndStatusNot(Collection<Long> userIds, SubscriptionStatus status);

	List<MemberSubscription> findByUserIdAndStatusNot(Long userId, SubscriptionStatus status);

	/**
	 * Distinct MEMBER accounts with an active account and a live subscription that covers today. Expiring ones are
	 * included, because they are still active until they end.
	 */
	@Query("""
			select count(distinct s.userId) from MemberSubscription s, User u
			where u.userId = s.userId and u.role = com.gym.management.entity.Role.MEMBER and u.active = true
			  and s.status in :live and s.startDate <= :today and s.endDate >= :today
			""")
	long countActiveMembers(@Param("today") LocalDate today, @Param("live") Collection<SubscriptionStatus> live);

	/** Distinct active MEMBER accounts whose live subscription ends by {@code cutoff} (today included). */
	@Query("""
			select count(distinct s.userId) from MemberSubscription s, User u
			where u.userId = s.userId and u.role = com.gym.management.entity.Role.MEMBER and u.active = true
			  and s.status in :live and s.startDate <= :today and s.endDate >= :today and s.endDate <= :cutoff
			""")
	long countExpiringMembers(@Param("today") LocalDate today, @Param("cutoff") LocalDate cutoff,
			@Param("live") Collection<SubscriptionStatus> live);

	/**
	 * True when a non-cancelled subscription of the member overlaps [start, end]. Used to block overlapping
	 * assignments and renewals.
	 */
	@Query("""
			select count(s) > 0 from MemberSubscription s
			where s.userId = :userId and s.status <> :cancelled
			  and s.startDate <= :end and s.endDate >= :start
			""")
	boolean existsOverlapping(@Param("userId") Long userId, @Param("start") LocalDate start,
			@Param("end") LocalDate end, @Param("cancelled") SubscriptionStatus cancelled);

}
