package com.gym.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Mapping of the existing {@code users} table. password_hash is deliberately not mapped, so this service
 * cannot read or write credentials; Hibernate never includes it in UPDATE statements. role has no setter:
 * it is set only when the row is created by the Auth Service and is never changed by this service.
 * Hibernate validates the mapped columns against the schema and never alters it.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(name = "email", nullable = false, length = 150)
	private String email;

	@Column(name = "phone", length = 20)
	private String phone;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "address", columnDefinition = "TEXT")
	private String address;

	@Column(name = "emergency_contact_name", length = 150)
	private String emergencyContactName;

	@Column(name = "emergency_contact_phone", length = 20)
	private String emergencyContactPhone;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.ENUM)
	@Column(name = "role", nullable = false)
	private Role role;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	/** Replaces the supported profile fields. The role and password are never touched here. */
	public void updateProfile(String fullName, String email, String phone, String address,
			String emergencyContactName, String emergencyContactPhone, LocalDateTime now) {
		this.fullName = fullName;
		this.email = email;
		this.phone = phone;
		this.address = address;
		this.emergencyContactName = emergencyContactName;
		this.emergencyContactPhone = emergencyContactPhone;
		this.updatedAt = now;
	}

	public void updateActiveStatus(boolean active, LocalDateTime now) {
		this.active = active;
		this.updatedAt = now;
	}

}
