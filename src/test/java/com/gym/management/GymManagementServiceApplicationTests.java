package com.gym.management;

import com.gym.management.repository.GymMediaRepository;
import com.gym.management.repository.MemberSubscriptionRepository;
import com.gym.management.repository.PaymentAllocationRepository;
import com.gym.management.repository.PaymentRepository;
import com.gym.management.repository.SubscriptionInstallmentRepository;
import com.gym.management.repository.SubscriptionPlanFeatureRepository;
import com.gym.management.repository.SubscriptionPlanRepository;
import com.gym.management.repository.UserRepository;
import com.gym.management.support.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies that the whole application context wires together without MySQL. The database is excluded and every
 * repository is mocked. Schema validation against the real database is verified separately (see the report).
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

	@MockitoBean
	MemberSubscriptionRepository memberSubscriptionRepository;

	@MockitoBean
	SubscriptionInstallmentRepository subscriptionInstallmentRepository;

	@MockitoBean
	SubscriptionPlanRepository subscriptionPlanRepository;

	@MockitoBean
	SubscriptionPlanFeatureRepository subscriptionPlanFeatureRepository;

	@MockitoBean
	PaymentRepository paymentRepository;

	@MockitoBean
	PaymentAllocationRepository paymentAllocationRepository;

	@MockitoBean
	GymMediaRepository gymMediaRepository;

	@DynamicPropertySource
	static void jwtSecret(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TestSupport.JWT_SECRET);
	}

	@Test
	void contextLoads() {
	}

}
