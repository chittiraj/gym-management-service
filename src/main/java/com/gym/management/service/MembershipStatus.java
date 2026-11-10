package com.gym.management.service;

/**
 * Membership status derived from subscription dates. This is not users.is_active, which is account status.
 * ACTIVE and EXPIRING are the filterable states. PENDING means only future subscriptions exist. NONE means the
 * member has never had a subscription.
 */
public enum MembershipStatus {
	ACTIVE,
	EXPIRING,
	EXPIRED,
	PENDING,
	NONE
}
