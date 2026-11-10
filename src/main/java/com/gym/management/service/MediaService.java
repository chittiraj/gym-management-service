package com.gym.management.service;

import com.gym.management.dto.MediaRequest;
import com.gym.management.dto.MediaResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.entity.GymMedia;
import com.gym.management.entity.MediaCategory;
import com.gym.management.entity.MediaType;
import com.gym.management.entity.User;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.GymMediaRepository;
import com.gym.management.repository.GymSpecifications;
import com.gym.management.repository.UserRepository;
import com.gym.management.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Media library records. Only URLs are stored. Deleting a record sets is_active to false and keeps the row.
 * uploaded_by is set from the authenticated caller when a record is created and is never changed afterwards.
 */
@Service
public class MediaService {

	static final String DEFAULT_SORT = "createdAt,desc";

	private static final Map<String, String> SORT_FIELDS = Map.of(
			"createdAt", "createdAt",
			"title", "title",
			"mediaType", "mediaType",
			"mediaCategory", "mediaCategory",
			"mediaId", "mediaId");

	private final GymMediaRepository mediaRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public MediaService(GymMediaRepository mediaRepository, UserRepository userRepository, Clock clock) {
		this.mediaRepository = mediaRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	@Transactional
	public MediaResponse create(MediaRequest request, CurrentUser caller) {
		if (!userRepository.existsById(caller.userId())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is no longer valid.");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		GymMedia media = mediaRepository.save(new GymMedia(
				request.title().trim(), blankToNull(request.description()), request.mediaType(),
				request.mediaCategory(), request.mediaUrl().trim(), caller.userId(),
				request.active() == null || request.active(), now));
		return toResponses(List.of(media)).get(0);
	}

	@Transactional(readOnly = true)
	public PageResponse<MediaResponse> list(MediaType type, MediaCategory category, Boolean active, String search,
			String sort, int page, int size) {
		Page<GymMedia> result = mediaRepository.findAll(
				GymSpecifications.mediaFilter(type, category, active, blankToNull(search)),
				PageRequest.of(page, size, parseSort(sort)));
		List<MediaResponse> responses = toResponses(result.getContent());
		Map<Long, MediaResponse> byId = responses.stream()
				.collect(Collectors.toMap(MediaResponse::mediaId, response -> response));
		return PageResponse.from(result.map(media -> byId.get(media.getMediaId())));
	}

	@Transactional(readOnly = true)
	public MediaResponse get(long mediaId) {
		return toResponses(List.of(findMedia(mediaId))).get(0);
	}

	@Transactional
	public MediaResponse update(long mediaId, MediaRequest request) {
		GymMedia media = findMedia(mediaId);
		media.update(request.title().trim(), blankToNull(request.description()), request.mediaType(),
				request.mediaCategory(), request.mediaUrl().trim(),
				request.active() == null || request.active(), LocalDateTime.now(clock));
		return toResponses(List.of(media)).get(0);
	}

	/** Soft delete. The row is kept, and deleting an already inactive record does nothing. */
	@Transactional
	public void delete(long mediaId) {
		GymMedia media = findMedia(mediaId);
		if (media.isActive()) {
			media.update(media.getTitle(), media.getDescription(), media.getMediaType(), media.getMediaCategory(),
					media.getMediaUrl(), false, LocalDateTime.now(clock));
		}
	}

	private GymMedia findMedia(long mediaId) {
		return mediaRepository.findById(mediaId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Media not found."));
	}

	private List<MediaResponse> toResponses(List<GymMedia> media) {
		if (media.isEmpty()) {
			return List.of();
		}
		List<Long> uploaderIds = media.stream().map(GymMedia::getUploadedBy).filter(id -> id != null).distinct().toList();
		Map<Long, String> names = uploaderIds.isEmpty() ? Map.of()
				: userRepository.findAllById(uploaderIds).stream()
						.collect(Collectors.toMap(User::getUserId, User::getFullName));
		return media.stream()
				.map(item -> new MediaResponse(
						item.getMediaId(),
						item.getTitle(),
						item.getDescription(),
						item.getMediaType(),
						item.getMediaCategory(),
						item.getMediaUrl(),
						item.getUploadedBy(),
						item.getUploadedBy() == null ? null : names.get(item.getUploadedBy()),
						item.isActive(),
						item.getCreatedAt(),
						item.getUpdatedAt()))
				.toList();
	}

	/** Accepts "field" or "field,asc|desc" for whitelisted fields. Defaults to newest first. */
	static Sort parseSort(String sort) {
		String value = sort == null || sort.isBlank() ? DEFAULT_SORT : sort.trim();
		String[] parts = value.split(",", -1);
		String property = parts.length <= 2 ? SORT_FIELDS.get(parts[0].trim()) : null;
		if (property == null) {
			throw unsupportedSort();
		}
		Sort.Direction direction = parts.length == 2 ? parseDirection(parts[1]) : Sort.Direction.ASC;
		Sort primary = Sort.by(direction, property);
		return "mediaId".equals(property) ? primary : primary.and(Sort.by(Sort.Direction.DESC, "mediaId"));
	}

	private static Sort.Direction parseDirection(String value) {
		try {
			return Sort.Direction.fromString(value.trim());
		} catch (IllegalArgumentException ex) {
			throw unsupportedSort();
		}
	}

	private static ApiException unsupportedSort() {
		return new ApiException(HttpStatus.BAD_REQUEST,
				"Unsupported sort. Use createdAt, title, mediaType, mediaCategory or mediaId, optionally followed by ,asc or ,desc.");
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
