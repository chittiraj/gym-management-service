package com.gym.management.service;

import com.gym.management.dto.CreateMemberRequest;
import com.gym.management.dto.MemberDetailResponse;
import com.gym.management.dto.MemberDirectoryItem;
import com.gym.management.dto.MemberResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.dto.UpdateMemberRequest;
import com.gym.management.entity.MemberSubscription;
import com.gym.management.entity.Role;
import com.gym.management.entity.User;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.GymSpecifications;
import com.gym.management.repository.LikePatterns;
import com.gym.management.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member operations. Every lookup is restricted to role MEMBER, so ADMIN and STAFF accounts are never returned or
 * modified through these methods. Membership status is derived from subscription dates, not users.is_active.
 */
@Service
public class MemberService {

	static final String DEFAULT_SORT = "userId,asc";

	/** Request sort names mapped to entity properties. Anything else is rejected, so sorting cannot reach unmapped fields. */
	private static final Map<String, String> SORT_FIELDS = Map.of(
			"userId", "userId",
			"fullName", "fullName",
			"email", "email",
			"createdAt", "createdAt");

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final UserRepository userRepository;
	private final AuthServiceClient authServiceClient;
	private final MembershipService membershipService;
	private final Clock clock;

	public MemberService(UserRepository userRepository, AuthServiceClient authServiceClient,
			MembershipService membershipService, Clock clock) {
		this.userRepository = userRepository;
		this.authServiceClient = authServiceClient;
		this.membershipService = membershipService;
		this.clock = clock;
	}

	/**
	 * Lists members. search matches name or email (case-insensitive), or the member ID when numeric. active filters
	 * account status. status filters membership status (ACTIVE or EXPIRING or EXPIRED), derived from dates.
	 */
	@Transactional(readOnly = true)
	public PageResponse<MemberDirectoryItem> findMembers(String search, Boolean active, MembershipStatus status,
			int page, int size, String sort) {
		Pageable pageable = PageRequest.of(page, size, parseSort(sort));
		Specification<User> spec = memberSpecification(normalizeSearch(search), active);
		if (status != null) {
			spec = spec.and(GymSpecifications.memberWithMembershipStatus(status, LocalDate.now(clock)));
		}
		Page<User> result = userRepository.findAll(spec, pageable);
		List<Long> ids = result.getContent().stream().map(User::getUserId).toList();
		Map<Long, MemberFinancials> snapshots = membershipService.snapshotsFor(ids);
		return PageResponse.from(result.map(user -> toDirectoryItem(user,
				snapshots.getOrDefault(user.getUserId(), MemberFinancials.none()))));
	}

	@Transactional(readOnly = true)
	public MemberDetailResponse getMember(long memberId) {
		User member = findMemberEntity(memberId);
		MemberFinancials financials = membershipService.snapshotsFor(List.of(memberId))
				.getOrDefault(memberId, MemberFinancials.none());
		return toDetail(member, financials);
	}

	/**
	 * Creates a member login account through the Auth Service, which hashes the password and forces role MEMBER.
	 * Not transactional: the HTTP call must not hold a database connection. The response is read back from users.
	 */
	public MemberResponse createMember(CreateMemberRequest request) {
		long userId = authServiceClient.createMemberAccount(request);
		User member = userRepository.findByUserIdAndRole(userId, Role.MEMBER)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_GATEWAY,
						"Created member could not be read back."));
		return toResponse(member);
	}

	/** Replaces the supported profile fields. The role and password are never changed here. */
	@Transactional
	public MemberResponse updateMember(long memberId, UpdateMemberRequest request) {
		User member = findMemberEntity(memberId);
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmailAndUserIdNot(email, memberId)) {
			throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
		}
		member.updateProfile(
				request.fullName().trim(),
				email,
				blankToNull(request.phone()),
				blankToNull(request.address()),
				blankToNull(request.emergencyContactName()),
				blankToNull(request.emergencyContactPhone()),
				LocalDateTime.now(clock));
		return toResponse(userRepository.save(member));
	}

	@Transactional
	public MemberResponse updateStatus(long memberId, boolean active) {
		User member = findMemberEntity(memberId);
		member.updateActiveStatus(active, LocalDateTime.now(clock));
		return toResponse(userRepository.save(member));
	}

	/** Loads a MEMBER account. Any other role is reported as not found. */
	User findMemberEntity(long memberId) {
		return userRepository.findByUserIdAndRole(memberId, Role.MEMBER)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found."));
	}

	static Specification<User> memberSpecification(String search, Boolean active) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.<Role>get("role"), Role.MEMBER));
			if (active != null) {
				predicates.add(cb.equal(root.<Boolean>get("active"), active));
			}
			if (search != null) {
				String pattern = LikePatterns.contains(search);
				List<Predicate> matches = new ArrayList<>();
				matches.add(cb.like(cb.lower(root.<String>get("fullName")), pattern, LikePatterns.ESCAPE));
				matches.add(cb.like(cb.lower(root.<String>get("email")), pattern, LikePatterns.ESCAPE));
				// At most 18 digits, so the value always fits in a long.
				if (search.matches("\\d{1,18}")) {
					matches.add(cb.equal(root.<Long>get("userId"), Long.valueOf(search)));
				}
				predicates.add(cb.or(matches.toArray(Predicate[]::new)));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/** Escapes LIKE wildcards so user input is matched literally. */
	static String escapeLike(String value) {
		return value
				.replace(String.valueOf(LikePatterns.ESCAPE), "" + LikePatterns.ESCAPE + LikePatterns.ESCAPE)
				.replace("%", LikePatterns.ESCAPE + "%")
				.replace("_", LikePatterns.ESCAPE + "_");
	}

	/** Accepts "field" or "field,asc|desc". Only whitelisted fields are allowed. userId is added as a tie-breaker. */
	static Sort parseSort(String sort) {
		String value = sort == null || sort.isBlank() ? DEFAULT_SORT : sort.trim();
		String[] parts = value.split(",", -1);
		if (parts.length > 2) {
			throw unsupportedSort();
		}
		String property = SORT_FIELDS.get(parts[0].trim());
		if (property == null) {
			throw unsupportedSort();
		}
		Sort.Direction direction = Sort.Direction.ASC;
		if (parts.length == 2) {
			try {
				direction = Sort.Direction.fromString(parts[1].trim());
			} catch (IllegalArgumentException ex) {
				throw unsupportedSort();
			}
		}
		Sort primary = Sort.by(direction, property);
		return "userId".equals(property) ? primary : primary.and(Sort.by(Sort.Direction.ASC, "userId"));
	}

	private static ApiException unsupportedSort() {
		return new ApiException(HttpStatus.BAD_REQUEST,
				"Unsupported sort. Use one of userId, fullName, email, createdAt, optionally followed by ,asc or ,desc.");
	}

	/** Parses a membership status filter. Only ACTIVE, EXPIRING and EXPIRED are accepted. */
	public static MembershipStatus parseMembershipFilter(String value) {
		if (value == null) {
			return null;
		}
		return switch (value.trim().toUpperCase(Locale.ROOT)) {
			case "ACTIVE" -> MembershipStatus.ACTIVE;
			case "EXPIRING" -> MembershipStatus.EXPIRING;
			case "EXPIRED" -> MembershipStatus.EXPIRED;
			default -> throw new ApiException(HttpStatus.BAD_REQUEST,
					"Unsupported status. Use ACTIVE, EXPIRING or EXPIRED.");
		};
	}

	static MemberResponse toResponse(User user) {
		return new MemberResponse(
				user.getUserId(),
				user.getFullName(),
				user.getEmail(),
				user.getPhone(),
				user.getAddress(),
				user.getEmergencyContactName(),
				user.getEmergencyContactPhone(),
				user.isActive(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}

	private static MemberDirectoryItem toDirectoryItem(User user, MemberFinancials financials) {
		MemberSubscription current = financials.currentSubscription();
		return new MemberDirectoryItem(
				user.getUserId(),
				user.getFullName(),
				user.getEmail(),
				user.getPhone(),
				user.isActive(),
				user.getCreatedAt(),
				financials.membershipStatus(),
				current == null ? null : current.getPlanNameSnapshot(),
				current == null ? null : current.getEndDate(),
				financials.outstandingBalance());
	}

	private static MemberDetailResponse toDetail(User user, MemberFinancials financials) {
		MemberSubscription current = financials.currentSubscription();
		MemberDetailResponse.CurrentSubscriptionSummary subscription = current == null ? null
				: new MemberDetailResponse.CurrentSubscriptionSummary(
						current.getSubscriptionId(),
						current.getPlanCodeSnapshot(),
						current.getPlanNameSnapshot(),
						current.getStartDate(),
						current.getEndDate(),
						current.getTotalAmount(),
						current.getPaymentOption(),
						current.getStatus());
		BigDecimal progress = null;
		if (current != null && financials.currentAmount().signum() > 0) {
			progress = financials.amountReceived().multiply(HUNDRED)
					.divide(financials.currentAmount(), 2, RoundingMode.HALF_UP);
		}
		MemberDetailResponse.PaymentSummary payments = current == null ? null
				: new MemberDetailResponse.PaymentSummary(
						financials.amountReceived(),
						financials.outstandingBalance(),
						financials.installmentCount(),
						financials.monthlyDue(),
						progress);
		return new MemberDetailResponse(
				user.getUserId(),
				user.getFullName(),
				user.getEmail(),
				user.getPhone(),
				user.getAddress(),
				user.getEmergencyContactName(),
				user.getEmergencyContactPhone(),
				user.isActive(),
				user.getCreatedAt(),
				user.getUpdatedAt(),
				financials.membershipStatus(),
				subscription,
				payments);
	}

	private static String normalizeSearch(String search) {
		return search == null || search.isBlank() ? null : search.trim();
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
