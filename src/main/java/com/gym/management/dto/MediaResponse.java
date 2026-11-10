package com.gym.management.dto;

import com.gym.management.entity.MediaCategory;
import com.gym.management.entity.MediaType;
import java.time.LocalDateTime;

public record MediaResponse(
		Long mediaId,
		String title,
		String description,
		MediaType mediaType,
		MediaCategory mediaCategory,
		String mediaUrl,
		Long uploadedBy,
		String uploadedByName,
		boolean isActive,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
}
