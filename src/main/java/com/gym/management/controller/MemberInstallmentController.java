package com.gym.management.controller;

import com.gym.management.dto.InstallmentResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.security.CurrentUser;
import com.gym.management.service.InstallmentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A member's installment schedule. ADMIN and STAFF may read any member; a MEMBER only their own. */
@RestController
public class MemberInstallmentController {

	private final InstallmentService installmentService;

	public MemberInstallmentController(InstallmentService installmentService) {
		this.installmentService = installmentService;
	}

	@GetMapping("/api/members/{id}/installments")
	public PageResponse<InstallmentResponse> memberInstallments(@PathVariable("id") @Positive long id,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal Jwt jwt) {
		return installmentService.forMember(id, CurrentUser.from(jwt), page, size);
	}

}
