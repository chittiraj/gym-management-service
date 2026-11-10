package com.gym.management.dto;

import com.gym.management.entity.Role;
import java.time.LocalDateTime;

/** Read-only user view. Excludes password_hash and emergency contact information. */
public record UserResponse(
		Long userId,
		String fullName,
		String email,
		String phone,
		String address,
		Role role,
		boolean isActive,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
}
