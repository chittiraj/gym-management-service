package com.gym.management.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Request bodies for subscription plans. Grouped in one file because they share the same rules. */
public final class PlanRequests {

	private PlanRequests() {
	}

	/** One feature line. included defaults to true when omitted. */
	public record FeatureRequest(
			@NotBlank @Size(max = 150) String featureName,
			@Size(max = 2000) String featureDescription,
			Boolean included
	) {
	}

	/** Creates a plan. features may be omitted. */
	public record CreatePlanRequest(
			@NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,30}$", message = "Plan code must be 2-30 upper-case letters, digits or underscores") String planCode,
			@NotBlank @Size(max = 100) String planName,
			@Size(max = 5000) String description,
			@NotNull @Min(1) @Max(36) Integer durationMonths,
			@NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal price,
			@Min(0) @Max(10000) Integer displayOrder,
			List<@Valid FeatureRequest> features
	) {
	}

	/**
	 * Full update of the plan fields. When features is present it replaces the whole feature list; when it is
	 * null the existing features are kept. Existing subscriptions keep their own snapshots and amounts.
	 */
	public record UpdatePlanRequest(
			@NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,30}$", message = "Plan code must be 2-30 upper-case letters, digits or underscores") String planCode,
			@NotBlank @Size(max = 100) String planName,
			@Size(max = 5000) String description,
			@NotNull @Min(1) @Max(36) Integer durationMonths,
			@NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal price,
			@Min(0) @Max(10000) Integer displayOrder,
			List<@Valid FeatureRequest> features
	) {
	}

	/** Activates or deactivates a plan. Shared by plans and any other resource that uses the same shape. */
	public record ActiveStatusRequest(@NotNull Boolean active) {
	}

}
