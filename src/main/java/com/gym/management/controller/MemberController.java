package com.gym.management.controller;

import com.gym.management.dto.CreateMemberRequest;
import com.gym.management.dto.MemberDetailResponse;
import com.gym.management.dto.MemberDirectoryItem;
import com.gym.management.dto.MemberResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.dto.UpdateMemberRequest;
import com.gym.management.dto.UpdateMemberStatusRequest;
import com.gym.management.service.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Member endpoints. Role rules are enforced in SecurityConfig: GET and PUT are ADMIN or STAFF, status changes
 * are ADMIN only, and all other methods on this path are ADMIN only. Member-scoped subscription, payment and
 * installment reads live in their own controllers and allow a MEMBER to read only their own record.
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

	static final int MAX_PAGE_SIZE = 100;

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping
	public PageResponse<MemberDirectoryItem> listMembers(
			@RequestParam(name = "search", required = false) @Size(max = 100) String search,
			@RequestParam(name = "active", required = false) Boolean active,
			@RequestParam(name = "status", required = false) @Size(max = 20) String status,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
			@RequestParam(name = "sort", required = false) @Size(max = 50) String sort) {
		return memberService.findMembers(search, active, MemberService.parseMembershipFilter(status), page, size, sort);
	}

	/** ADMIN only, enforced in SecurityConfig. The created account always has role MEMBER. */
	@PostMapping
	public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody CreateMemberRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(memberService.createMember(request));
	}

	@GetMapping("/{id}")
	public MemberDetailResponse getMember(@PathVariable("id") @Positive long id) {
		return memberService.getMember(id);
	}

	@PutMapping("/{id}")
	public MemberResponse updateMember(@PathVariable("id") @Positive long id,
			@Valid @RequestBody UpdateMemberRequest request) {
		return memberService.updateMember(id, request);
	}

	@PatchMapping("/{id}/status")
	public MemberResponse updateStatus(@PathVariable("id") @Positive long id,
			@Valid @RequestBody UpdateMemberStatusRequest request) {
		return memberService.updateStatus(id, request.active());
	}

}
