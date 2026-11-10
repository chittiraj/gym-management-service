package com.gym.management.dto;

import com.gym.management.entity.MediaCategory;
import com.gym.management.entity.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Media metadata. mediaUrl must be an absolute http or https URL. This service stores the URL only; it does not
 * accept file uploads. active defaults to true when omitted. uploaded_by is never taken from the request.
 */
public record MediaRequest(
		@NotBlank @Size(max = 150) String title,
		@Size(max = 2000) String description,
		@NotNull MediaType mediaType,
		@NotNull MediaCategory mediaCategory,
		@NotBlank @Size(max = 1000) @Pattern(regexp = "^https?://\\S+$", message = "Media URL must start with http:// or https://") String mediaUrl,
		Boolean active
) {
}
