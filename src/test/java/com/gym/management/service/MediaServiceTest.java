package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gym.management.dto.MediaRequest;
import com.gym.management.dto.MediaResponse;
import com.gym.management.entity.GymMedia;
import com.gym.management.entity.MediaCategory;
import com.gym.management.entity.MediaType;
import com.gym.management.entity.Role;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.GymMediaRepository;
import com.gym.management.repository.UserRepository;
import com.gym.management.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 10, 10, 0);
	private static final CurrentUser ADMIN = new CurrentUser(1L, Role.ADMIN);

	@Mock
	GymMediaRepository mediaRepository;
	@Mock
	UserRepository userRepository;

	private MediaService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
		service = new MediaService(mediaRepository, userRepository, clock);
	}

	private static MediaRequest request(Boolean active) {
		return new MediaRequest("Squat form", "Back squat demo", MediaType.VIDEO, MediaCategory.WORKOUT,
				"https://cdn.example.com/squat.mp4", active);
	}

	private static GymMedia media(long id, Long uploadedBy, boolean active) {
		GymMedia media = new GymMedia("Title", null, MediaType.IMAGE, MediaCategory.GYM,
				"https://cdn.example.com/a.jpg", uploadedBy, active, NOW);
		ReflectionTestUtils.setField(media, "mediaId", id);
		return media;
	}

	@Test
	void createSetsUploadedByFromTheCallerAndDefaultsToActive() {
		when(userRepository.existsById(1L)).thenReturn(true);
		when(mediaRepository.save(any(GymMedia.class))).thenAnswer(invocation -> {
			GymMedia saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "mediaId", 10L);
			return saved;
		});
		when(userRepository.findAllById(any())).thenReturn(java.util.List.of());

		MediaResponse response = service.create(request(null), ADMIN);

		assertThat(response.uploadedBy()).isEqualTo(1L);
		assertThat(response.isActive()).isTrue();
		assertThat(response.mediaType()).isEqualTo(MediaType.VIDEO);
	}

	@Test
	void createFailsWhenTheCallerAccountNoLongerExists() {
		when(userRepository.existsById(1L)).thenReturn(false);

		assertThatThrownBy(() -> service.create(request(true), ADMIN))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
		verify(mediaRepository, never()).save(any());
	}

	@Test
	void updateChangesMetadataButNeverUploadedBy() {
		GymMedia existing = media(10L, 1L, true);
		when(mediaRepository.findById(10L)).thenReturn(Optional.of(existing));
		when(userRepository.findAllById(any())).thenReturn(java.util.List.of());

		service.update(10L, new MediaRequest("New title", null, MediaType.IMAGE, MediaCategory.GYM,
				"https://cdn.example.com/new.jpg", true));

		assertThat(existing.getTitle()).isEqualTo("New title");
		assertThat(existing.getUploadedBy()).isEqualTo(1L);
	}

	@Test
	void deleteIsASoftDeleteThatKeepsTheRow() {
		GymMedia existing = media(10L, 1L, true);
		when(mediaRepository.findById(10L)).thenReturn(Optional.of(existing));

		service.delete(10L);

		assertThat(existing.isActive()).isFalse();
		verify(mediaRepository, never()).delete(any(GymMedia.class));
		verify(mediaRepository, never()).deleteById(anyLong());
	}

	@Test
	void deletingAnUnknownMediaItemIsNotFound() {
		when(mediaRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99L))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void defaultSortIsNewestFirstWithIdTieBreaker() {
		Sort sort = MediaService.parseSort(null);

		assertThat(sort).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "mediaId")));
	}

	@Test
	void sortOnAnUnlistedFieldIsRejected() {
		assertThatThrownBy(() -> MediaService.parseSort("uploadedBy,asc"))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> MediaService.parseSort("title,sideways"))
				.isInstanceOf(ApiException.class);
	}

}
