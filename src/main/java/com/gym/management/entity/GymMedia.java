package com.gym.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps gym_media. The table stores URLs only, so this service manages media records and does not store files.
 * Deletion is a soft delete: the row stays and is_active becomes false.
 */
@Entity
@Table(name = "gym_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GymMedia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "media_id")
	private Long mediaId;

	@Column(name = "title", nullable = false, length = 150)
	private String title;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.ENUM)
	@Column(name = "media_type", nullable = false)
	private MediaType mediaType;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.ENUM)
	@Column(name = "media_category", nullable = false)
	private MediaCategory mediaCategory;

	@Column(name = "media_url", nullable = false, length = 1000)
	private String mediaUrl;

	@Column(name = "uploaded_by")
	private Long uploadedBy;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public GymMedia(String title, String description, MediaType mediaType, MediaCategory mediaCategory,
			String mediaUrl, Long uploadedBy, boolean active, LocalDateTime now) {
		this.title = title;
		this.description = description;
		this.mediaType = mediaType;
		this.mediaCategory = mediaCategory;
		this.mediaUrl = mediaUrl;
		this.uploadedBy = uploadedBy;
		this.active = active;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Edits the descriptive fields. uploaded_by is never changed by an edit. */
	public void update(String title, String description, MediaType mediaType, MediaCategory mediaCategory,
			String mediaUrl, boolean active, LocalDateTime now) {
		this.title = title;
		this.description = description;
		this.mediaType = mediaType;
		this.mediaCategory = mediaCategory;
		this.mediaUrl = mediaUrl;
		this.active = active;
		this.updatedAt = now;
	}

}
