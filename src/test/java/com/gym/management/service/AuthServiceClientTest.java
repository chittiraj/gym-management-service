package com.gym.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.gym.management.config.AuthServiceProperties;
import com.gym.management.dto.CreateMemberRequest;
import com.gym.management.exception.ApiException;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Verifies the outbound internal call with a mocked Auth Service: URL, method, key header and error mapping. */
class AuthServiceClientTest {

	private static final String BASE = "http://auth.test";
	private static final String KEY = "management-test-key-long-enough-for-the-check-0123456789";
	private static final String CREATE_URL = BASE + "/api/internal/members";

	private MockRestServiceServer server;
	private AuthServiceClient client;

	private void configure(String serviceKey) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new AuthServiceClient(builder.build(),
				new AuthServiceProperties(BASE, serviceKey, Duration.ofSeconds(2), Duration.ofSeconds(5)));
	}

	private static CreateMemberRequest request() {
		return new CreateMemberRequest("New Member", "new.member@gmail.com", "Member@123", "9876543210",
				"Hyderabad", "Asha", "9123456780");
	}

	private ApiException failure(Runnable call) {
		try {
			call.run();
		} catch (ApiException ex) {
			return ex;
		}
		throw new AssertionError("Expected ApiException");
	}

	@Test
	void successSendsServiceKeyAndReturnsNewUserId() {
		configure(KEY);
		server.expect(requestTo(CREATE_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("X-Internal-Service-Key", KEY))
				.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
						.body("{\"userId\":42,\"fullName\":\"New Member\",\"role\":\"MEMBER\",\"isActive\":true}"));

		assertThat(client.createMemberAccount(request())).isEqualTo(42L);
		server.verify();
	}

	@Test
	void roleOtherThanMemberInResponseIsRejected() {
		configure(KEY);
		server.expect(requestTo(CREATE_URL))
				.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
						.body("{\"userId\":42,\"role\":\"ADMIN\"}"));

		ApiException ex = failure(() -> client.createMemberAccount(request()));
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
	}

	@Test
	void emptySuccessBodyIsRejected() {
		configure(KEY);
		server.expect(requestTo(CREATE_URL)).andRespond(withStatus(HttpStatus.CREATED));

		ApiException ex = failure(() -> client.createMemberAccount(request()));
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
	}

	@Test
	void duplicateEmailFromAuthIsConflict() {
		configure(KEY);
		server.expect(requestTo(CREATE_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

		ApiException ex = failure(() -> client.createMemberAccount(request()));
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void validationFailureFromAuthIsBadRequest() {
		configure(KEY);
		server.expect(requestTo(CREATE_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		ApiException ex = failure(() -> client.createMemberAccount(request()));
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void keyRejectedByAuthIsBadGatewayAndMessageHidesKey() {
		configure(KEY);
		server.expect(requestTo(CREATE_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		ApiException ex = failure(() -> client.createMemberAccount(request()));
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(ex.getMessage()).doesNotContain(KEY);
	}

	@Test
	void authServerErrorIsBadGateway() {
		configure(KEY);
		server.expect(requestTo(CREATE_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		ApiException ex = failure(() -> client.createMemberAccount(request()));
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
	}

	@Test
	void unreachableAuthServiceIsServiceUnavailable() {
		configure(KEY);
		server.expect(requestTo(CREATE_URL)).andRespond(withException(new IOException("connection refused")));

		ApiException ex = failure(() -> client.createMemberAccount(request()));
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	void unconfiguredServiceKeyMakesNoCallAtAll() {
		configure("");
		// No expectations are registered, so any outbound request would fail the server's verification.

		ApiException ex = failure(() -> client.createMemberAccount(request()));
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		server.verify();
	}

}
