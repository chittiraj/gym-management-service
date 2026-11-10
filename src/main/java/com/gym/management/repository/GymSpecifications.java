package com.gym.management.repository;

import com.gym.management.entity.GymMedia;
import com.gym.management.entity.InstallmentStatus;
import com.gym.management.entity.MediaCategory;
import com.gym.management.entity.MediaType;
import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.Payment;
import com.gym.management.entity.PaymentMethod;
import com.gym.management.entity.PaymentStatus;
import com.gym.management.entity.Role;
import com.gym.management.entity.SubscriptionInstallment;
import com.gym.management.entity.SubscriptionPlan;
import com.gym.management.entity.SubscriptionStatus;
import com.gym.management.entity.User;
import com.gym.management.service.MembershipCalculator;
import com.gym.management.service.MembershipStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query filters. The membership predicates implement the same definitions as MembershipCalculator, so a status
 * filter and a displayed status always agree. The installment predicates implement InstallmentRules.statusFor.
 */
public final class GymSpecifications {

	private GymSpecifications() {
	}

	// ---------- Members by membership status ----------

	/** MEMBER accounts whose membership status (derived from subscription dates) matches. */
	public static Specification<User> memberWithMembershipStatus(MembershipStatus status, LocalDate today) {
		LocalDate cutoff = today.plusDays(MembershipCalculator.EXPIRING_WINDOW_DAYS);
		return (root, query, cb) -> {
			Predicate isMember = cb.equal(root.get("role"), Role.MEMBER);
			Predicate matches = switch (status) {
				case ACTIVE -> cb.exists(validSubscription(root, query, cb, today, cutoff, Bucket.LONG_TERM));
				case EXPIRING -> cb.exists(validSubscription(root, query, cb, today, cutoff, Bucket.EXPIRING));
				case EXPIRED -> cb.and(
						cb.not(cb.exists(validSubscription(root, query, cb, today, cutoff, Bucket.ANY))),
						cb.not(cb.exists(pendingSubscription(root, query, cb, today))),
						cb.exists(endedSubscription(root, query, cb, today)));
				default -> throw new IllegalArgumentException("Membership status is not filterable: " + status);
			};
			return cb.and(isMember, matches);
		};
	}

	private enum Bucket {
		ANY, LONG_TERM, EXPIRING
	}

	private static Subquery<Long> validSubscription(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb,
			LocalDate today, LocalDate cutoff, Bucket bucket) {
		Subquery<Long> sub = query.subquery(Long.class);
		Root<MemberSubscription> s = sub.from(MemberSubscription.class);
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(cb.equal(s.get("userId"), root.get("userId")));
		predicates.add(live(cb, s));
		predicates.add(cb.lessThanOrEqualTo(s.<LocalDate>get("startDate"), today));
		predicates.add(cb.greaterThanOrEqualTo(s.<LocalDate>get("endDate"), today));
		if (bucket == Bucket.LONG_TERM) {
			predicates.add(cb.greaterThan(s.<LocalDate>get("endDate"), cutoff));
		} else if (bucket == Bucket.EXPIRING) {
			predicates.add(cb.lessThanOrEqualTo(s.<LocalDate>get("endDate"), cutoff));
		}
		return sub.select(s.<Long>get("subscriptionId")).where(predicates.toArray(Predicate[]::new));
	}

	private static Subquery<Long> pendingSubscription(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb,
			LocalDate today) {
		Subquery<Long> sub = query.subquery(Long.class);
		Root<MemberSubscription> s = sub.from(MemberSubscription.class);
		return sub.select(s.<Long>get("subscriptionId")).where(
				cb.equal(s.get("userId"), root.get("userId")),
				live(cb, s),
				cb.greaterThan(s.<LocalDate>get("startDate"), today));
	}

	private static Subquery<Long> endedSubscription(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb,
			LocalDate today) {
		Subquery<Long> sub = query.subquery(Long.class);
		Root<MemberSubscription> s = sub.from(MemberSubscription.class);
		return sub.select(s.<Long>get("subscriptionId")).where(
				cb.equal(s.get("userId"), root.get("userId")),
				cb.notEqual(s.get("status"), SubscriptionStatus.CANCELLED),
				cb.lessThan(s.<LocalDate>get("endDate"), today));
	}

	/** PENDING or ACTIVE. The dates decide whether the subscription is current, future or ended. */
	private static Predicate live(CriteriaBuilder cb, Root<MemberSubscription> s) {
		return cb.or(cb.equal(s.get("status"), SubscriptionStatus.PENDING),
				cb.equal(s.get("status"), SubscriptionStatus.ACTIVE));
	}

	/** Member directory search: name or email (case-insensitive), or the numeric user id when the term is numeric. */
	public static Specification<User> memberSearch(String term) {
		return (root, query, cb) -> {
			String pattern = LikePatterns.contains(term);
			List<Predicate> matches = new ArrayList<>();
			matches.add(cb.like(cb.lower(root.<String>get("fullName")), pattern, LikePatterns.ESCAPE));
			matches.add(cb.like(cb.lower(root.<String>get("email")), pattern, LikePatterns.ESCAPE));
			if (term.matches("\\d{1,18}")) {
				matches.add(cb.equal(root.<Long>get("userId"), Long.valueOf(term)));
			}
			return cb.or(matches.toArray(Predicate[]::new));
		};
	}

	// ---------- Plans ----------

	public static Specification<SubscriptionPlan> planFilter(Boolean active, String search) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (active != null) {
				predicates.add(cb.equal(root.get("active"), active));
			}
			if (search != null) {
				String pattern = LikePatterns.contains(search);
				predicates.add(cb.or(
						cb.like(cb.lower(root.<String>get("planName")), pattern, LikePatterns.ESCAPE),
						cb.like(cb.lower(root.<String>get("planCode")), pattern, LikePatterns.ESCAPE)));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	// ---------- Subscriptions ----------

	public static Specification<MemberSubscription> subscriptionFilter(SubscriptionStatus status, Long userId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (userId != null) {
				predicates.add(cb.equal(root.get("userId"), userId));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/** ACTIVE subscriptions covering today whose end date falls between today and {@code until}, inclusive. */
	public static Specification<MemberSubscription> expiringBetween(LocalDate today, LocalDate until) {
		return (root, query, cb) -> cb.and(
				live(cb, root),
				cb.lessThanOrEqualTo(root.<LocalDate>get("startDate"), today),
				cb.greaterThanOrEqualTo(root.<LocalDate>get("endDate"), today),
				cb.lessThanOrEqualTo(root.<LocalDate>get("endDate"), until));
	}

	/** Non-cancelled subscriptions whose end date is before today. Includes historical records. */
	public static Specification<MemberSubscription> endedBefore(LocalDate today) {
		return (root, query, cb) -> cb.and(
				cb.notEqual(root.get("status"), SubscriptionStatus.CANCELLED),
				cb.lessThan(root.<LocalDate>get("endDate"), today));
	}

	// ---------- Installments ----------

	/**
	 * Installments filtered by display status (see InstallmentRules.statusFor), by subscription, and by member
	 * through the member's subscriptions. pendingOnly keeps installments with money still owed.
	 */
	public static Specification<SubscriptionInstallment> installmentFilter(InstallmentStatus status, Long subscriptionId,
			Long memberId, boolean pendingOnly, LocalDate today) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			var paid = root.<BigDecimal>get("paidAmount");
			var due = root.<BigDecimal>get("dueAmount");
			var dueDate = root.<LocalDate>get("dueDate");
			if (pendingOnly) {
				predicates.add(cb.lessThan(paid, due));
			}
			if (status != null) {
				predicates.add(switch (status) {
					case PAID -> cb.greaterThanOrEqualTo(paid, due);
					case OVERDUE -> cb.and(cb.lessThan(paid, due), cb.lessThan(dueDate, today));
					case PARTIAL -> cb.and(cb.greaterThan(paid, BigDecimal.ZERO), cb.lessThan(paid, due),
							cb.greaterThanOrEqualTo(dueDate, today));
					case UPCOMING -> cb.and(cb.equal(paid, BigDecimal.ZERO), cb.greaterThanOrEqualTo(dueDate, today));
				});
			}
			if (subscriptionId != null) {
				predicates.add(cb.equal(root.get("subscriptionId"), subscriptionId));
			}
			if (memberId != null) {
				Subquery<Long> memberSubscriptions = query.subquery(Long.class);
				Root<MemberSubscription> s = memberSubscriptions.from(MemberSubscription.class);
				memberSubscriptions.select(s.<Long>get("subscriptionId"))
						.where(cb.equal(s.get("userId"), memberId));
				predicates.add(root.get("subscriptionId").in(memberSubscriptions));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	// ---------- Payments ----------

	/** Payments filtered by status, method, member, subscription and a half-open date range [from, to). */
	public static Specification<Payment> paymentFilter(PaymentStatus status, PaymentMethod method, Long userId,
			Long subscriptionId, LocalDateTime from, LocalDateTime to) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(cb.equal(root.get("paymentStatus"), status));
			}
			if (method != null) {
				predicates.add(cb.equal(root.get("paymentMethod"), method));
			}
			if (userId != null) {
				predicates.add(cb.equal(root.get("userId"), userId));
			}
			if (subscriptionId != null) {
				predicates.add(cb.equal(root.get("subscriptionId"), subscriptionId));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("paymentDate"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThan(root.<LocalDateTime>get("paymentDate"), to));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	// ---------- Media ----------

	public static Specification<GymMedia> mediaFilter(MediaType type, MediaCategory category, Boolean active,
			String search) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (type != null) {
				predicates.add(cb.equal(root.get("mediaType"), type));
			}
			if (category != null) {
				predicates.add(cb.equal(root.get("mediaCategory"), category));
			}
			if (active != null) {
				predicates.add(cb.equal(root.get("active"), active));
			}
			if (search != null) {
				predicates.add(cb.like(cb.lower(root.<String>get("title")), LikePatterns.contains(search),
						LikePatterns.ESCAPE));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
