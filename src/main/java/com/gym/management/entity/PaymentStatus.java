package com.gym.management.entity;

/** Mirrors payments.payment_status ENUM('PENDING','SUCCESS','FAILED','REFUNDED'). Only SUCCESS counts as collected. */
public enum PaymentStatus {
	PENDING,
	SUCCESS,
	FAILED,
	REFUNDED
}
