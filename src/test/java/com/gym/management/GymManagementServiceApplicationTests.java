package com.gym.management;

import com.gym.management.repository.UserRepository;
import com.gym.management.support.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies that the whole application context wires together without MySQL. The database is excluded and
 * the repository is mocked.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
class GymManagementServiceApplicationTests {

	@MockitoBean
	UserRepository userRepository;

	@DynamicPropertySource
	static void jwtSecret(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TestSupport.JWT_SECRET);
	}

	@Test
	void contextLoads() {
	}

}
