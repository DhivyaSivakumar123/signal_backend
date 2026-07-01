package com.signal.scoring.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signal.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
public class TriageServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TriageServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TriageServiceClient(RestClient.Builder restClientBuilder,
                               @Value("${signal.services.triage-url:http://localhost:8084}") String triageServiceUrl,
                               ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(triageServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    public record BiasResponse(int biasDelta) {}

    public int getSenderBias(UUID userId, String senderId) {
        log.info("Fetching sender bias for user {} and sender {} from triage-service", userId, senderId);
        try {
            String rawResponse = restClient.get()
                    .uri("/api/triage/feedback/bias/" + userId + "/" + senderId)
                    .header("X-User-Id", userId.toString())
                    .retrieve()
                    .body(String.class);

            ApiResponse<BiasResponse> response = objectMapper.readValue(rawResponse, new TypeReference<ApiResponse<BiasResponse>>() {});
            if (response != null && response.data() != null) {
                return response.data().biasDelta();
            }
            return 0;
        } catch (Exception e) {
            log.warn("Failed to fetch sender bias: {}. Defaulting to 0.", e.getMessage());
            return 0;
        }
    }

    public record PreferenceResponse(String statedPriorities) {}

    public String getStatedPriorities(UUID userId) {
        log.info("Fetching stated priorities for user {} from triage-service", userId);
        try {
            String rawResponse = restClient.get()
                    .uri("/api/triage/preferences/" + userId)
                    .header("X-User-Id", userId.toString())
                    .retrieve()
                    .body(String.class);

            ApiResponse<PreferenceResponse> response = objectMapper.readValue(rawResponse, new TypeReference<ApiResponse<PreferenceResponse>>() {});
            if (response != null && response.data() != null) {
                return response.data().statedPriorities();
            }
            return "";
        } catch (Exception e) {
            log.warn("Failed to fetch user preferences: {}. Defaulting to empty.", e.getMessage());
            return "";
        }
    }
}
