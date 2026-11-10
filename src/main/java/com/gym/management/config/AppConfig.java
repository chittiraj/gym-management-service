package com.gym.management.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AuthServiceProperties.class)
public class AppConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}

	/** Client for the Auth Service internal API, with bounded connect and read timeouts. */
	@Bean
	public RestClient authServiceRestClient(AuthServiceProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		// Built directly rather than from an injected RestClient.Builder: Boot 4 does not auto-configure one without an extra starter.
		return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}

}
