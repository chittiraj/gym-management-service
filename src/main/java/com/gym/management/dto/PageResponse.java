package com.gym.management.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable pagination envelope, so the API does not expose Spring Data's PageImpl serialisation. */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean last
) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isLast());
	}

}
