package com.gym.management.repository;

import com.gym.management.entity.Role;
import com.gym.management.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

	/** Returns active users only. Inactive accounts are excluded by default. */
	Page<User> findByActiveTrue(Pageable pageable);

	/** Member lookup. Any other role is treated as not found, so administrative accounts are never exposed here. */
	Optional<User> findByUserIdAndRole(Long userId, Role role);

	/** Email uniqueness check that ignores the member being updated. */
	boolean existsByEmailAndUserIdNot(String email, Long userId);

	/** Total accounts with the given role. */
	long countByRole(Role role);

	/** Accounts with the given role and is_active = true (account status, not membership status). */
	long countByRoleAndActiveTrue(Role role);

}
