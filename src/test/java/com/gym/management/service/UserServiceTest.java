package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gym.management.dto.PageResponse;
import com.gym.management.dto.UserResponse;
import com.gym.management.entity.Role;
import com.gym.management.entity.User;
import com.gym.management.repository.UserRepository;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	UserRepository userRepository;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository);
	}

	/** The entity is read-only with a protected constructor, so the test instantiates it reflectively. */
	private static User userEntity(long id, boolean active) {
		User user = BeanUtils.instantiateClass(User.class);
		ReflectionTestUtils.setField(user, "userId", id);
		ReflectionTestUtils.setField(user, "fullName", "Member " + id);
		ReflectionTestUtils.setField(user, "email", "member" + id + "@gmail.com");
		ReflectionTestUtils.setField(user, "role", Role.MEMBER);
		ReflectionTestUtils.setField(user, "active", active);
		return user;
	}

	@Test
	void listsActiveUsersOnlyOrderedByUserId() {
		Page<User> page = new PageImpl<>(List.of(userEntity(1L, true)));
		when(userRepository.findByActiveTrue(any(Pageable.class))).thenReturn(page);

		PageResponse<UserResponse> response = userService.findActiveUsers(0, 20);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(userRepository).findByActiveTrue(pageable.capture());
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "userId"));
		assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).userId()).isEqualTo(1L);
	}

	@Test
	void inactiveUsersAreNotLoadedByDefault() {
		when(userRepository.findByActiveTrue(any(Pageable.class))).thenReturn(Page.empty());

		userService.findActiveUsers(0, 20);

		verify(userRepository, never()).findAll(any(Pageable.class));
		verify(userRepository, never()).findAll();
	}

	@Test
	void userResponseHasNoPasswordOrEmergencyContactFields() {
		List<String> components = Arrays.stream(UserResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(components).noneMatch(name -> name.toLowerCase().contains("password"));
		assertThat(components).noneMatch(name -> name.toLowerCase().contains("emergency"));
	}

}
