package com.gym.management.repository;

import com.gym.management.entity.GymMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GymMediaRepository extends JpaRepository<GymMedia, Long>, JpaSpecificationExecutor<GymMedia> {
}
