package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gym.management.dto.CreateMemberRequest;
import com.gym.management.dto.MemberDetailResponse;
import com.gym.management.dto.MemberDirectoryItem;
import com.gym.management.dto.MemberResponse;
import com.gym.management.dto.PageResponse;
import com.gym.management.dto.UpdateMemberRequest;
import com.gym.management.entity.Role;
import com.gym.management.entity.User;
import com.gym.management.exception.ApiException;
import com.gym.management.repository.UserRepository;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 11, 10, 9, 30);

	@Mock
	UserRepository userRepository;

	@Mock
	AuthServiceClient authServiceClient;

	@Mock
	MembershipService membershipService;

	private MemberService memberService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
		memberService = new MemberService(userRepository, authServiceClient, membershipService, clock);
	}

	/** The entity has a protected constructor and no setters, so the test populates it reflectively. */
	private static User userEntity(long id, Role role, boolean active) {
		User user = BeanUtils.instantiateClass(User.class);
		ReflectionTestUtils.setField(user, "userId", id);
		ReflectionTestUtils.setField(user, "fullName", "Member " + id);
		ReflectionTestUtils.setField(user, "email", "member" + id + "@gmail.com");
		ReflectionTestUtils.setField(user, "role", role);
		ReflectionTestUtils.setField(user, "active", active);
		ReflectionTestUtils.setField(user, "createdAt", FIXED_NOW);
		ReflectionTestUtils.setField(user, "updatedAt", FIXED_NOW);
		return user;
	}

	// ---------- Listing: member-only, search, paging and sorting ----------

	@Test
	void listUsesDefaultSortAndSpecificationForMembers() {
		Page<User> page = new PageImpl<>(List.of(userEntity(1L, Role.MEMBER, true)));
		when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), any(Pageable.class)))
				.thenReturn(page);

		PageResponse<MemberDirectoryItem> response = memberService.findMembers(null, null, null, 0, 20, null);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), pageable.capture());
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "userId"));
		assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
		assertThat(response.content()).extracting(MemberDirectoryItem::userId).containsExactly(1L);
	}

	@Test
	void listAcceptsWhitelistedSortWithDirection() {
		when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), any(Pageable.class)))
				.thenReturn(Page.empty());

		memberService.findMembers("ann", true, null, 2, 10, "fullName,desc");

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), pageable.capture());
		assertThat(pageable.getValue().getSort())
				.isEqualTo(Sort.by(Sort.Direction.DESC, "fullName").and(Sort.by(Sort.Direction.ASC, "userId")));
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
	}

	@Test
	void sortOnUnknownPropertyIsRejectedBeforeQuerying() {
		assertThatThrownBy(() -> memberService.findMembers(null, null, null, 0, 20, "password_hash"))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		verify(userRepository, never()).findAll(ArgumentMatchers.<Specification<User>>any(), any(Pageable.class));
	}

	@Test
	void sortWithUnknownDirectionIsRejected() {
		assertThatThrownBy(() -> MemberService.parseSort("email,sideways"))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> MemberService.parseSort("email,asc,extra"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void likeWildcardsInSearchAreEscaped() {
		assertThat(MemberService.escapeLike("50%_off!")).isEqualTo("50!%!_off!!");
		assertThat(MemberService.escapeLike("plain")).isEqualTo("plain");
	}

	// ---------- Member details ----------

	@Test
	void memberDetailsIncludeProfileAndEmergencyContact() {
		User user = userEntity(5L, Role.MEMBER, true);
		ReflectionTestUtils.setField(user, "emergencyContactName", "Raj");
		ReflectionTestUtils.setField(user, "emergencyContactPhone", "9876500000");
		when(userRepository.findByUserIdAndRole(5L, Role.MEMBER)).thenReturn(Optional.of(user));

		MemberDetailResponse response = memberService.getMember(5L);

		assertThat(response.userId()).isEqualTo(5L);
		assertThat(response.emergencyContactName()).isEqualTo("Raj");
		assertThat(response.emergencyContactPhone()).isEqualTo("9876500000");
	}

	@Test
	void nonMemberAccountIsNotFound() {
		// ADMIN and STAFF ids resolve to nothing through the MEMBER-restricted lookup.
		when(userRepository.findByUserIdAndRole(1L, Role.MEMBER)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> memberService.getMember(1L))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void memberResponseHasNoPasswordRoleOrCredentialFields() {
		List<String> components = Arrays.stream(MemberResponse.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(components).noneMatch(name -> name.toLowerCase().contains("password"));
		assertThat(components).doesNotContain("role");
	}

	// ---------- Updates ----------

	@Test
	void updateChangesProfileButNeverRoleOrPassword() {
		User user = userEntity(5L, Role.MEMBER, true);
		when(userRepository.findByUserIdAndRole(5L, Role.MEMBER)).thenReturn(Optional.of(user));
		when(userRepository.existsByEmailAndUserIdNot("new.name@gmail.com", 5L)).thenReturn(false);
		when(userRepository.save(user)).thenReturn(user);

		UpdateMemberRequest request = new UpdateMemberRequest("  New Name ", "  New.Name@Gmail.com ",
				"9876543210", "Hyderabad", "Emergency", "9123456780");

		MemberResponse response = memberService.updateMember(5L, request);

		assertThat(response.fullName()).isEqualTo("New Name");
		assertThat(response.email()).isEqualTo("new.name@gmail.com");
		assertThat(response.emergencyContactName()).isEqualTo("Emergency");
		assertThat(user.getRole()).isEqualTo(Role.MEMBER);
		assertThat(user.getUpdatedAt()).isEqualTo(FIXED_NOW);
	}

	@Test
	void updateRequestHasNoRoleComponent() {
		List<String> components = Arrays.stream(UpdateMemberRequest.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(components).doesNotContain("role");
	}

	@Test
	void emailAlreadyUsedByAnotherAccountIsConflict() {
		User user = userEntity(5L, Role.MEMBER, true);
		when(userRepository.findByUserIdAndRole(5L, Role.MEMBER)).thenReturn(Optional.of(user));
		when(userRepository.existsByEmailAndUserIdNot("taken@gmail.com", 5L)).thenReturn(true);

		UpdateMemberRequest request = new UpdateMemberRequest("Name", "taken@gmail.com", null, null, null, null);

		assertThatThrownBy(() -> memberService.updateMember(5L, request))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
		verify(userRepository, never()).save(ArgumentMatchers.any(User.class));
	}

	@Test
	void updatingNonMemberIsNotFoundAndNothingIsSaved() {
		when(userRepository.findByUserIdAndRole(9L, Role.MEMBER)).thenReturn(Optional.empty());

		UpdateMemberRequest request = new UpdateMemberRequest("Name", "x@gmail.com", null, null, null, null);

		assertThatThrownBy(() -> memberService.updateMember(9L, request)).isInstanceOf(ApiException.class);
		verify(userRepository, never()).save(ArgumentMatchers.any(User.class));
	}

	// ---------- Admin-created members ----------

	private static CreateMemberRequest createRequest() {
		return new CreateMemberRequest("New Member", "new.member@gmail.com", "Member@123", "9876543210",
				"Hyderabad", "Asha", "9123456780");
	}

	@Test
	void createdMemberIsReadBackFromStoredRecord() {
		when(authServiceClient.createMemberAccount(any(CreateMemberRequest.class))).thenReturn(42L);
		User stored = userEntity(42L, Role.MEMBER, true);
		when(userRepository.findByUserIdAndRole(42L, Role.MEMBER)).thenReturn(Optional.of(stored));

		MemberResponse response = memberService.createMember(createRequest());

		assertThat(response.userId()).isEqualTo(42L);
		assertThat(response.isActive()).isTrue();
		verify(authServiceClient).createMemberAccount(createRequest());
	}

	@Test
	void createdAccountMissingFromMemberViewIsBadGateway() {
		when(authServiceClient.createMemberAccount(any(CreateMemberRequest.class))).thenReturn(42L);
		when(userRepository.findByUserIdAndRole(42L, Role.MEMBER)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> memberService.createMember(createRequest()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
	}

	@Test
	void authServiceFailureIsPropagatedWithoutLocalWrite() {
		when(authServiceClient.createMemberAccount(any(CreateMemberRequest.class)))
				.thenThrow(new ApiException(HttpStatus.CONFLICT, "An account with this email already exists."));

		assertThatThrownBy(() -> memberService.createMember(createRequest())).isInstanceOf(ApiException.class);
		verify(userRepository, never()).save(ArgumentMatchers.any(User.class));
	}

	@Test
	void createRequestToStringDoesNotExposePassword() {
		assertThat(createRequest().toString()).doesNotContain("Member@123").doesNotContain("new.member@gmail.com");
	}

	// ---------- Activation and deactivation ----------

	@Test
	void deactivateSetsInactiveWithCurrentTimestamp() {
		User user = userEntity(5L, Role.MEMBER, true);
		when(userRepository.findByUserIdAndRole(5L, Role.MEMBER)).thenReturn(Optional.of(user));
		when(userRepository.save(user)).thenReturn(user);

		MemberResponse response = memberService.updateStatus(5L, false);

		assertThat(response.isActive()).isFalse();
		assertThat(user.getUpdatedAt()).isEqualTo(FIXED_NOW);
		verify(userRepository).save(user);
	}

	@Test
	void activateSetsActive() {
		User user = userEntity(5L, Role.MEMBER, false);
		when(userRepository.findByUserIdAndRole(5L, Role.MEMBER)).thenReturn(Optional.of(user));
		when(userRepository.save(user)).thenReturn(user);

		assertThat(memberService.updateStatus(5L, true).isActive()).isTrue();
	}

}
