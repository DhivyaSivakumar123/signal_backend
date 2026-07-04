package com.signal.integration.poller;

import com.signal.common.dto.MessageSource;
import com.signal.integration.entity.Integration;
import com.signal.integration.repository.IntegrationRepository;
import com.signal.integration.service.MockIntegrationClient;
import com.signal.integration.service.PollerCoordinatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class EmailPoller {

    private static final Logger log = LoggerFactory.getLogger(EmailPoller.class);

    private final IntegrationRepository integrationRepository;
    private final MockIntegrationClient mockIntegrationClient;
    private final PollerCoordinatorService pollerCoordinatorService;
    private final String googleClientId;
    private final String googleClientSecret;
    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public EmailPoller(IntegrationRepository integrationRepository,
                       MockIntegrationClient mockIntegrationClient,
                       PollerCoordinatorService pollerCoordinatorService,
                       @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.google.client-id:dummy}") String googleClientId,
                       @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.google.client-secret:dummy}") String googleClientSecret) {
        this.integrationRepository = integrationRepository;
        this.mockIntegrationClient = mockIntegrationClient;
        this.pollerCoordinatorService = pollerCoordinatorService;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
    }

    @Scheduled(fixedDelay = 180000, initialDelay = 15000)
    public void pollEmails() {
        log.info("Starting Email integration polling...");
        List<Integration> emailIntegrations = integrationRepository.findByStatus("ACTIVE").stream()
                .filter(i -> i.getProvider() == MessageSource.GMAIL || i.getProvider() == MessageSource.OUTLOOK)
                .toList();

        if (emailIntegrations.isEmpty()) {
            log.info("No active Email integrations found.");
            return;
        }

        for (Integration integration : emailIntegrations) {
            log.info("Polling {} for user: {}", integration.getProvider(), integration.getUserId());
            try {
                // Stagger/jitter execution to prevent thundering herd
                Thread.sleep(50 + new java.util.Random().nextInt(150));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (integration.getProvider() == MessageSource.GMAIL && integration.getRefreshToken() != null) {
                pollRealGmail(integration);
            } else {
                List<MockIntegrationClient.MockMessage> emails = mockIntegrationClient.getEmails();
                for (MockIntegrationClient.MockMessage email : emails) {
                    pollerCoordinatorService.processIncomingMessage(
                            integration.getUserId(),
                            integration.getProvider(),
                            email.threadExternalId(),
                            email.senderId(),
                            email.senderName(),
                            email.subjectOrPreview(),
                            email.fullContent()
                    );
                }
            }
            integration.setLastSyncedAt(Instant.now());
            integrationRepository.save(integration);
        }
    }

    private void pollRealGmail(Integration integration) {
        log.info("Polling real Gmail API for user: {}", integration.getUserId());
        String accessToken = refreshAccessToken(integration.getRefreshToken());
        if (accessToken == null) {
            log.warn("Failed to refresh access token for user {}. Marking integration as EXPIRED.", integration.getUserId());
            integration.setStatus("EXPIRED");
            integrationRepository.save(integration);
            return;
        }

        try {
            String listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=is:unread";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(accessToken);
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    listUrl,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );

            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.getBody());
            com.fasterxml.jackson.databind.JsonNode messages = root.path("messages");

            if (messages.isArray()) {
                log.info("Found {} unread Gmail messages for user {}", messages.size(), integration.getUserId());
                for (com.fasterxml.jackson.databind.JsonNode msgRef : messages) {
                    String msgId = msgRef.path("id").asText();
                    
                    String detailUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + msgId;
                    org.springframework.http.ResponseEntity<String> detailResponse = restTemplate.exchange(
                            detailUrl,
                            org.springframework.http.HttpMethod.GET,
                            entity,
                            String.class
                    );
                    
                    com.fasterxml.jackson.databind.JsonNode msgNode = objectMapper.readTree(detailResponse.getBody());
                    String threadId = msgNode.path("threadId").asText();
                    String snippet = msgNode.path("snippet").asText();
                    
                    String from = getHeader(msgNode, "From");
                    String subject = getHeader(msgNode, "Subject");

                    String senderName = from;
                    String senderId = from;
                    if (from.contains("<") && from.contains(">")) {
                        senderName = from.substring(0, from.indexOf("<")).trim();
                        senderId = from.substring(from.indexOf("<") + 1, from.indexOf(">")).trim();
                    }

                    pollerCoordinatorService.processIncomingMessage(
                            integration.getUserId(),
                            MessageSource.GMAIL,
                            threadId,
                            senderId,
                            senderName,
                            subject.isEmpty() ? "(No Subject)" : subject,
                            snippet
                    );
                }
            } else {
                log.info("No new unread emails for user {}", integration.getUserId());
            }
        } catch (Exception e) {
            log.error("Failed to poll real Gmail for user " + integration.getUserId(), e);
        }
    }

    private String refreshAccessToken(String refreshToken) {
        try {
            String tokenUrl = "https://oauth2.googleapis.com/token";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

            org.springframework.util.MultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("client_id", googleClientId);
            body.add("client_secret", googleClientSecret);
            body.add("refresh_token", refreshToken);
            body.add("grant_type", "refresh_token");

            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> requestEntity = new org.springframework.http.HttpEntity<>(body, headers);
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, requestEntity, String.class);

            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("access_token").asText();
        } catch (Exception e) {
            log.error("Failed to refresh Google access token", e);
            return null;
        }
    }

    private String getHeader(com.fasterxml.jackson.databind.JsonNode messageNode, String name) {
        com.fasterxml.jackson.databind.JsonNode headers = messageNode.path("payload").path("headers");
        if (headers.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode header : headers) {
                if (name.equalsIgnoreCase(header.path("name").asText())) {
                    return header.path("value").asText();
                }
            }
        }
        return "";
    }
}
