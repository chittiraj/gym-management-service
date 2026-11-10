package com.gym.management.controller;

import com.gym.management.dto.MediaRequest;
import com.gym.management.dto.MediaResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.entity.MediaCategory;
import com.gym.management.entity.MediaType;
import com.gym.management.security.CurrentUser;
import com.gym.management.service.MediaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Media library (URL-based). ADMIN and STAFF may read, create and edit. Deleting is ADMIN only and is a soft delete.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

	private final MediaService mediaService;

	public MediaController(MediaService mediaService) {
		this.mediaService = mediaService;
	}

	@PostMapping
	public ResponseEntity<MediaResponse> create(@Valid @RequestBody MediaRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.create(request, CurrentUser.from(jwt)));
	}

	@GetMapping
	public PageResponse<MediaResponse> list(
			@RequestParam(name = "type", required = false) MediaType type,
			@RequestParam(name = "category", required = false) MediaCategory category,
			@RequestParam(name = "active", required = false) Boolean active,
			@RequestParam(name = "search", required = false) @Size(max = 100) String search,
			@RequestParam(name = "sort", required = false) @Size(max = 60) String sort,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
		return mediaService.list(type, category, active, search, sort, page, size);
	}

	@GetMapping("/{id}")
	public MediaResponse get(@PathVariable("id") @Positive long id) {
		return mediaService.get(id);
	}

	@PutMapping("/{id}")
	public MediaResponse update(@PathVariable("id") @Positive long id, @Valid @RequestBody MediaRequest request) {
		return mediaService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") @Positive long id) {
		mediaService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
