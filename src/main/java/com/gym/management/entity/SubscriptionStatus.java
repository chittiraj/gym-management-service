package com.gym.management.entity;

/** Mirrors the MySQL ENUM('PENDING','ACTIVE','EXPIRED','CANCELLED') column member_subscriptions.status. */
public enum SubscriptionStatus {
	PENDING,
	ACTIVE,
	EXPIRED,
	CANCELLED
}
