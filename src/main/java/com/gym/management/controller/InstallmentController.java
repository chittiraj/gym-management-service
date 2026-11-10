package com.gym.management.controller;

import com.gym.management.dto.InstallmentResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.dto.PaymentRequests.RecordInstallmentPaymentRequest;
import com.gym.management.dto.PaymentResponse;
import com.gym.management.entity.InstallmentStatus;
import com.gym.management.security.CurrentUser;
import com.gym.management.service.InstallmentService;
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

/** Installments. ADMIN and STAFF may read and pay. A MEMBER may read only their own, via /api/members/{id}/installments. */
@RestController
@RequestMapping("/api/installments")
public class InstallmentController {

	private final InstallmentService installmentService;

	public InstallmentController(InstallmentService installmentService) {
		this.installmentService = installmentService;
	}

	@GetMapping
	public PageResponse<InstallmentResponse> list(
			@RequestParam(name = "status", required = false) InstallmentStatus status,
			@RequestParam(name = "subscriptionId", required = false) @Positive Long subscriptionId,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
		return installmentService.list(status, subscriptionId, page, size);
	}

	@GetMapping("/pending")
	public PageResponse<InstallmentResponse> pending(
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
		return installmentService.pending(page, size);
	}

	@GetMapping("/{id}")
	public InstallmentResponse get(@PathVariable("id") @Positive long id) {
		return installmentService.get(id);
	}

	@PostMapping("/{id}/payments")
	public ResponseEntity<PaymentResponse> pay(@PathVariable("id") @Positive long id,
			@Valid @RequestBody RecordInstallmentPaymentRequest request, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.status(HttpStatus.CREATED).body(installmentService.pay(id, request, CurrentUser.from(jwt)));
	}

}
