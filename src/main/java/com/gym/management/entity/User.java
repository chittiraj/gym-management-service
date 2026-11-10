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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Read-only view of the existing {@code users} table. password_hash and the emergency contact columns are
 * deliberately not mapped, so this service cannot read or write credentials. Hibernate validates only
 * the mapped columns against the schema and never alters it.
 */
@Entity
@Table(name = "users")
@Immutable
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

}
