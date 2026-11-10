package com.gym.management.controller;

import com.gym.management.dto.PageResponse;
import com.gym.management.dto.PlanRequests.ActiveStatusRequest;
import com.gym.management.dto.PlanRequests.CreatePlanRequest;
import com.gym.management.dto.PlanRequests.UpdatePlanRequest;
import com.gym.management.dto.PlanResponse;
import com.gym.management.service.PlanService;
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

/** Plans. ADMIN only for writes; ADMIN and STAFF may read. Rules are in SecurityConfig. */
@RestController
@RequestMapping("/api/plans")
public class PlanController {

	private final PlanService planService;

	public PlanController(PlanService planService) {
		this.planService = planService;
	}

	@PostMapping
	public ResponseEntity<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(request));
	}

	@GetMapping
	public PageResponse<PlanResponse> list(
			@RequestParam(name = "active", required = false) Boolean active,
			@RequestParam(name = "search", required = false) @Size(max = 100) String search,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
		return planService.list(active, search, page, size);
	}

	@GetMapping("/{id}")
	public PlanResponse get(@PathVariable("id") @Positive long id) {
		return planService.get(id);
	}

	@PutMapping("/{id}")
	public PlanResponse update(@PathVariable("id") @Positive long id, @Valid @RequestBody UpdatePlanRequest request) {
		return planService.update(id, request);
	}

	@PatchMapping("/{id}/status")
	public PlanResponse setStatus(@PathVariable("id") @Positive long id, @Valid @RequestBody ActiveStatusRequest request) {
		return planService.setActive(id, request.active());
	}

}
