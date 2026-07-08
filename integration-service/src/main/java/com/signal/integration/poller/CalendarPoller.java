package com.signal.integration.poller;

import com.signal.common.dto.MessageSource;
import com.signal.integration.entity.Integration;
import com.signal.integration.repository.IntegrationRepository;
import com.signal.integration.service.PollerCoordinatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class CalendarPoller {

    private static final Logger log = LoggerFactory.getLogger(CalendarPoller.class);

    private final IntegrationRepository integrationRepository;
    private final PollerCoordinatorService pollerCoordinatorService;
    private final String googleClientId;
    private final String googleClientSecret;
    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public CalendarPoller(IntegrationRepository integrationRepository,
            PollerCoordinatorService pollerCoordinatorService,
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.google.client-id:dummy}") String googleClientId,
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.google.client-secret:dummy}") String googleClientSecret) {
        this.integrationRepository = integrationRepository;
        this.pollerCoordinatorService = pollerCoordinatorService;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 20000)
    public void pollCalendar() {
        log.info("Starting Calendar integration polling...");
        List<Integration> calendarIntegrations = integrationRepository.findByStatus("ACTIVE").stream()
                .filter(i -> i.getProvider() == MessageSource.CALENDAR)
                .toList();

        if (calendarIntegrations.isEmpty()) {
            log.info("No active Calendar integrations found.");
            return;
        }

        for (Integration integration : calendarIntegrations) {
            try {
                // Stagger/jitter execution to prevent thundering herd
                Thread.sleep(50 + new java.util.Random().nextInt(150));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pollIntegration(integration);
        }
    }

    public void pollIntegration(Integration integration) {
        log.info("Polling Calendar for user: {}", integration.getUserId());
        if (integration.getRefreshToken() != null) {
            pollRealCalendar(integration);
        } else {
            log.info("Mock calendar event generation is disabled. Skipping sync for Calendar (user: {})", integration.getUserId());
        }
        integration.setLastSyncedAt(Instant.now());
        integrationRepository.save(integration);
    }

    private void pollRealCalendar(Integration integration) {
        log.info("Polling real Google Calendar API for user: {}", integration.getUserId());
        String accessToken = refreshAccessToken(integration.getRefreshToken());
        if (accessToken == null) {
            log.warn("Failed to refresh access token for user {}. Marking integration as EXPIRED.",
                    integration.getUserId());
            integration.setStatus("EXPIRED");
            integrationRepository.save(integration);
            return;
        }

        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ISO_INSTANT;
            String timeMin = formatter.format(Instant.now().minusSeconds(1800)); // last 30 mins
            String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin="
                    + java.net.URLEncoder.encode(timeMin, java.nio.charset.StandardCharsets.UTF_8);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(accessToken);
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class);

            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.getBody());
            com.fasterxml.jackson.databind.JsonNode items = root.path("items");

            if (items.isArray()) {
                log.info("Found {} Calendar events for user {}", items.size(), integration.getUserId());
                for (com.fasterxml.jackson.databind.JsonNode event : items) {
                    String eventId = event.path("id").asText();
                    String summary = event.path("summary").asText("No Title");
                    String description = event.path("description").asText("");

                    String startStr = event.path("start").has("dateTime")
                            ? event.path("start").path("dateTime").asText()
                            : event.path("start").path("date").asText();
                    String endStr = event.path("end").has("dateTime") ? event.path("end").path("dateTime").asText()
                            : event.path("end").path("date").asText();

                    String detail = "Start: " + startStr + ", End: " + endStr + ". " + description;

                    pollerCoordinatorService.processIncomingMessage(
                            integration.getUserId(),
                            MessageSource.CALENDAR,
                            eventId,
                            "calendar_system",
                            "Calendar System",
                            summary,
                            detail);
                }
            } else {
                log.info("No new calendar events for user {}", integration.getUserId());
            }
        } catch (Exception e) {
            log.error("Failed to poll Google Calendar for user " + integration.getUserId(), e);
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

            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> requestEntity = new org.springframework.http.HttpEntity<>(
                    body, headers);
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl,
                    requestEntity, String.class);

            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("access_token").asText();
        } catch (Exception e) {
            log.error("Failed to refresh Google access token", e);
            return null;
        }
    }
}
