package com.signal.scoring.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signal.common.dto.ApiResponse;
import com.signal.common.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AuthServiceClient(RestClient.Builder restClientBuilder, 
                             @Value("${signal.services.auth-url:http://localhost:8081}") String authServiceUrl,
                             ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(authServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    public UserDTO getUser(UUID userId) {
        log.info("Fetching user profile for {} from auth-service", userId);
        try {
            String rawResponse = restClient.get()
                    .uri("/api/auth/users/" + userId)
                    .header("X-User-Id", userId.toString())
                    .retrieve()
                    .body(String.class);

            ApiResponse<UserDTO> response = objectMapper.readValue(rawResponse, new TypeReference<ApiResponse<UserDTO>>() {});
            if (response != null && response.data() != null) {
                return response.data();
            }
            throw new RuntimeException("Empty response from auth-service");
        } catch (Exception e) {
            log.error("Failed to fetch user profile from auth-service: {}. Using local fallback.", e.getMessage());
            return new UserDTO(
                    userId, 
                    "developer@acme.com", 
                    "Dev IC", 
                    UUID.fromString("11111111-1111-1111-1111-111111111111"), 
                    "IC", 
                    UUID.fromString("22222222-2222-2222-2222-222222222222"), 
                    "DEV",
                    ""
            );
        }
    }
}
