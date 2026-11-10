package com.gym.management.service;

import com.gym.management.dto.PageResponse;
import com.gym.management.dto.UserResponse;
import com.gym.management.entity.User;
import com.gym.management.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/** Lists active users only, ordered by immutable user_id. */
	@Transactional(readOnly = true)
	public PageResponse<UserResponse> findActiveUsers(int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "userId"));
		Page<User> result = userRepository.findByActiveTrue(pageable);
		return PageResponse.from(result.map(UserService::toResponse));
	}

	static UserResponse toResponse(User user) {
		return new UserResponse(
				user.getUserId(),
				user.getFullName(),
				user.getEmail(),
				user.getPhone(),
				user.getAddress(),
				user.getRole(),
				user.isActive(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}

}
