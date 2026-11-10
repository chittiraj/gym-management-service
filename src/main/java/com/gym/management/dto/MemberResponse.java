package com.gym.management.dto;

import java.time.LocalDateTime;

/**
 * Member profile view. Every member has role MEMBER, so the role is not included. password_hash is never
 * included. Subscription, plan, trainer and balance fields are not included because the schema for those
 * tables has not been provided to this service (see the delivery report).
 */
public record MemberResponse(
		Long userId,
		String fullName,
		String email,
		String phone,
		String address,
		String emergencyContactName,
		String emergencyContactPhone,
		boolean isActive,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
}
