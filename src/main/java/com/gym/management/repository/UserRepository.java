package com.gym.management.repository;

import com.gym.management.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** Returns active users only. Inactive accounts are excluded by default. */
	Page<User> findByActiveTrue(Pageable pageable);

}
