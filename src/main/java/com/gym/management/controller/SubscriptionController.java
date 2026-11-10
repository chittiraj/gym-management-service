package com.gym.management.controller;

import com.gym.management.dto.PageResponse;
import com.gym.management.dto.SubscriptionRequests.CreateSubscriptionRequest;
import com.gym.management.dto.SubscriptionRequests.RenewSubscriptionRequest;
import com.gym.management.dto.SubscriptionResponse;
import com.gym.management.entity.SubscriptionStatus;
import com.gym.management.security.CurrentUser;
import com.gym.management.service.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subscriptions. Assigning and renewing are ADMIN only. Reads are ADMIN and STAFF, and a MEMBER may read only
 * their own current subscription through /api/members/{id}/subscription. Rules are in SecurityConfig.
 */
@RestController
public class SubscriptionController {

	private final SubscriptionService subscriptionService;

	public SubscriptionController(SubscriptionService subscriptionService) {
		this.subscriptionService = subscriptionService;
	}

	@PostMapping("/api/subscriptions")
	public ResponseEntity<SubscriptionResponse.Detail> create(@Valid @RequestBody CreateSubscriptionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.create(request));
	}

	@GetMapping("/api/subscriptions")
	public PageResponse<SubscriptionResponse> list(
			@RequestParam(name = "status", required = false) SubscriptionStatus status,
			@RequestParam(name = "userId", required = false) @Positive Long userId,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
		return subscriptionService.list(status, userId, page, size);
	}

	/** Subscriptions ending within the next {@code days} days (today included). Defaults to 7. */
	@GetMapping("/api/subscriptions/expiring")
	public PageResponse<SubscriptionResponse> expiring(
			@RequestParam(name = "days", defaultValue = "7") @Min(1) @Max(90) int days,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
		return subscriptionService.expiring(days, page, size);
	}

	/** Non-cancelled subscriptions that ended before today, including historical records. */
	@GetMapping("/api/subscriptions/expired")
	public PageResponse<SubscriptionResponse> expired(
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
		return subscriptionService.expired(page, size);
	}

	@GetMapping("/api/subscriptions/{id}")
	public SubscriptionResponse.Detail get(@PathVariable("id") @Positive long id) {
		return subscriptionService.get(id);
	}

	@PostMapping("/api/subscriptions/{id}/renew")
	public ResponseEntity<SubscriptionResponse.Detail> renew(@PathVariable("id") @Positive long id,
			@Valid @RequestBody RenewSubscriptionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.renew(id, request));
	}

	/** The member's current subscription. ADMIN and STAFF may read any member; a MEMBER only their own. */
	@GetMapping("/api/members/{id}/subscription")
	public SubscriptionResponse.Detail memberSubscription(@PathVariable("id") @Positive long id,
			@AuthenticationPrincipal Jwt jwt) {
		return subscriptionService.currentForMember(id, CurrentUser.from(jwt));
	}

}
