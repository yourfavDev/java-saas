package com.libraries.saas.config;

import com.libraries.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AuthConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public UserRepository userRepository(
            RestTemplate restTemplate,
            @Value("${keycloak.base-url}") String baseUrl,
            @Value("${keycloak.client-id}") String clientId,
            @Value("${keycloak.client-secret}") String clientSecret,
            @Value("${keycloak.realm}") String realm
    ) {
        return new UserRepository(
                restTemplate,
                baseUrl,
                clientId,
                clientSecret,
                realm
        );
    }
}
