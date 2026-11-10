package com.gym.management.controller;

import com.gym.management.dto.InvoiceResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.dto.PaymentRequests.RecordPaymentRequest;
import com.gym.management.dto.PaymentResponse;
import com.gym.management.entity.PaymentMethod;
import com.gym.management.entity.PaymentStatus;
import com.gym.management.security.CurrentUser;
import com.gym.management.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Payments. ADMIN and STAFF record and read payments. A MEMBER may read only their own history, via
 * /api/members/{id}/payments. The recorded-by user is taken from the verified JWT.
 */
@RestController
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/api/payments")
	public ResponseEntity<PaymentResponse> record(@Valid @RequestBody RecordPaymentRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(paymentService.recordForSubscription(request, CurrentUser.from(jwt)));
	}

	@GetMapping("/api/payments")
	public PageResponse<PaymentResponse> list(
			@RequestParam(name = "status", required = false) PaymentStatus status,
			@RequestParam(name = "method", required = false) PaymentMethod method,
			@RequestParam(name = "userId", required = false) @Positive Long userId,
			@RequestParam(name = "subscriptionId", required = false) @Positive Long subscriptionId,
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
		return paymentService.list(status, method, userId, subscriptionId, from, to, page, size);
	}

	@GetMapping("/api/payments/{id}")
	public PaymentResponse get(@PathVariable("id") @Positive long id) {
		return paymentService.get(id);
	}

	@GetMapping("/api/payments/{id}/invoice")
	public InvoiceResponse invoice(@PathVariable("id") @Positive long id) {
		return paymentService.invoice(id);
	}

	/** A member's payment history. ADMIN and STAFF may read any member; a MEMBER only their own. */
	@GetMapping("/api/members/{id}/payments")
	public PageResponse<PaymentResponse> memberPayments(@PathVariable("id") @Positive long id,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal Jwt jwt) {
		return paymentService.memberPayments(id, CurrentUser.from(jwt), page, size);
	}

}
