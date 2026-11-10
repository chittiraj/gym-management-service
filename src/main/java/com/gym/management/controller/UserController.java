package com.gym.management.controller;

import com.gym.management.dto.PageResponse;
import com.gym.management.dto.UserResponse;
import com.gym.management.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

	static final int MAX_PAGE_SIZE = 100;

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	/** ADMIN and STAFF only; the role restriction is enforced in SecurityConfig. */
	@GetMapping("/users")
	public PageResponse<UserResponse> listUsers(
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		return userService.findActiveUsers(page, size);
	}

}
