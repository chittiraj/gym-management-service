package com.gym.management.service;

import com.gym.management.config.AuthServiceProperties;
import com.gym.management.dto.CreateMemberRequest;
import com.gym.management.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Calls the Auth Service internal account-creation endpoint. The Auth Service hashes the password and always
 * creates a MEMBER. The caller's ADMIN token is not forwarded. The service key and the password are never
 * included in logs or error messages.
 */
@Component
public class AuthServiceClient {

	static final String SERVICE_KEY_HEADER = "X-Internal-Service-Key";
	static final String INTERNAL_MEMBERS_PATH = "/api/internal/members";

	private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

	private final RestClient restClient;
	private final String serviceKey;

	public AuthServiceClient(RestClient authServiceRestClient, AuthServiceProperties properties) {
		this.restClient = authServiceRestClient;
		this.serviceKey = properties.serviceKey();
	}

	/** Creates the login account and returns its user id. Fails unless the Auth Service confirms role MEMBER. */
	public long createMemberAccount(CreateMemberRequest request) {
		if (serviceKey == null || serviceKey.isBlank()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Member account creation is not configured.");
		}
		AccountCreatedResponse created;
		try {
			created = restClient.post()
					.uri(INTERNAL_MEMBERS_PATH)
					.header(SERVICE_KEY_HEADER, serviceKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.body(AccountCreatedResponse.class);
		} catch (HttpClientErrorException ex) {
			HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
			if (status == HttpStatus.CONFLICT) {
				throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
			}
			if (status == HttpStatus.BAD_REQUEST) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "The account service rejected the member details.");
			}
			// 401 here means the two services hold different keys. That is a configuration fault, not a client error.
			log.error("Account service rejected the internal call with status {}", ex.getStatusCode().value());
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Account service rejected the internal request.");
		} catch (HttpServerErrorException ex) {
			log.error("Account service failed with status {}", ex.getStatusCode().value());
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Account service failed to create the member.");
		} catch (ResourceAccessException ex) {
			log.error("Account service is unreachable: {}", ex.getClass().getSimpleName());
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Account service is unavailable.");
		}

		if (created == null || created.userId() == null || !"MEMBER".equals(created.role())) {
			log.error("Account service returned an unexpected response for member creation");
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Account service returned an unexpected response.");
		}
		return created.userId();
	}

	/** Only the fields this service needs. Other fields in the Auth Service response are ignored. */
	record AccountCreatedResponse(Long userId, String role) {
	}

}
