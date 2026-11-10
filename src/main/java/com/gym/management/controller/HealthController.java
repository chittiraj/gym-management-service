package com.gym.management.controller;

import com.gym.management.dto.HealthResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

	/** Public endpoint (see SecurityConfig). Reports process liveness only. */
	@GetMapping("/health")
	public HealthResponse health() {
		return new HealthResponse("UP", "gym-management-service", Instant.now());
	}

}
