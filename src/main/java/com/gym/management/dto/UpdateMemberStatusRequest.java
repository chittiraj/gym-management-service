package com.gym.management.dto;

import jakarta.validation.constraints.NotNull;

/** Activates ({@code true}) or deactivates ({@code false}) a member account. */
public record UpdateMemberStatusRequest(@NotNull Boolean active) {
}
