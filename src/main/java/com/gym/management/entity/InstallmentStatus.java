package com.gym.management.entity;

/**
 * Mirrors subscription_installments.status ENUM('UPCOMING','PARTIAL','PAID','OVERDUE'). The stored value is
 * refreshed whenever a payment is allocated. Reads use the same rules, so OVERDUE also appears for unpaid
 * installments whose due date has passed.
 */
public enum InstallmentStatus {
	UPCOMING,
	PARTIAL,
	PAID,
	OVERDUE
}
