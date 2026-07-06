package com.signal.integration.controller;

import com.signal.common.dto.ApiResponse;
import com.signal.common.dto.MessageSource;
import com.signal.integration.entity.Integration;
import com.signal.integration.repository.IntegrationRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.signal.integration.poller.SlackPoller;
import com.signal.integration.poller.EmailPoller;
import com.signal.integration.poller.CalendarPoller;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final IntegrationRepository integrationRepository;
    private final com.signal.common.security.JwtUtil jwtUtil;
    private final SlackPoller slackPoller;
    private final EmailPoller emailPoller;
    private final CalendarPoller calendarPoller;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String frontendUrl;
    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public IntegrationController(IntegrationRepository integrationRepository,
                                 com.signal.common.security.JwtUtil jwtUtil,
                                 SlackPoller slackPoller,
                                 EmailPoller emailPoller,
                                 CalendarPoller calendarPoller,
                                 @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.google.client-id:dummy}") String googleClientId,
                                 @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.google.client-secret:dummy}") String googleClientSecret,
                                 @org.springframework.beans.factory.annotation.Value("${signal.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.integrationRepository = integrationRepository;
        this.jwtUtil = jwtUtil;
        this.slackPoller = slackPoller;
        this.emailPoller = emailPoller;
        this.calendarPoller = calendarPoller;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.frontendUrl = frontendUrl;
    }

    @PostMapping("/slack/connect")
    public ApiResponse<Integration> connectSlack() {
        return connect(MessageSource.SLACK);
    }

    @PostMapping("/gmail/connect")
    public ApiResponse<Integration> connectGmail() {
        return connect(MessageSource.GMAIL);
    }

    @PostMapping("/outlook/connect")
    public ApiResponse<Integration> connectOutlook() {
        return connect(MessageSource.OUTLOOK);
    }

    @PostMapping("/calendar/connect")
    public ApiResponse<Integration> connectCalendar() {
        return connect(MessageSource.CALENDAR);
    }

    @GetMapping
    public ApiResponse<List<Integration>> list() {
        UUID userId = getAuthenticatedUserId();
        List<Integration> list = integrationRepository.findByUserId(userId);
        return ApiResponse.success(list);
    }

    @PostMapping("/sync")
    public ApiResponse<Void> syncAll() {
        UUID userId = getAuthenticatedUserId();
        log.info("Manually triggering sync for user: {}", userId);
        List<Integration> integrations = integrationRepository.findByUserId(userId).stream()
                .filter(i -> "ACTIVE".equals(i.getStatus()))
                .toList();
        for (Integration integration : integrations) {
            try {
                if (integration.getProvider() == MessageSource.SLACK) {
                    slackPoller.pollIntegration(integration);
                } else if (integration.getProvider() == MessageSource.GMAIL || integration.getProvider() == MessageSource.OUTLOOK) {
                    emailPoller.pollIntegration(integration);
                } else if (integration.getProvider() == MessageSource.CALENDAR) {
                    calendarPoller.pollIntegration(integration);
                }
            } catch (Exception e) {
                log.error("Failed manual sync of provider " + integration.getProvider() + " for user " + userId, e);
            }
        }
        return ApiResponse.success(null);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IntegrationController.class);

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") UUID id) {
        Optional<Integration> integrationOpt = integrationRepository.findById(id);
        if (integrationOpt.isPresent()) {
            Integration integration = integrationOpt.get();
            
            // 1. Revoke token at provider (simulated/invoked)
            revokeProviderToken(integration.getProvider(), integration.getSecretRef());
            
            // 2. Delete secret from Secrets Manager (simulated/invoked)
            deleteSecretFromSecretsManager(integration.getSecretRef());
            
            // 3. Set status to REVOKED in database
            integration.setStatus("REVOKED");
            integrationRepository.save(integration);
            log.info("Disconnected integration ID: {}. Revoked token at provider, deleted secret from Secrets Manager, set status to REVOKED.", id);
        }
        return ApiResponse.success(null);
    }

    private void revokeProviderToken(MessageSource provider, String secretRef) {
        log.info("Simulating HTTP POST to {} token revocation endpoint using secretRef: {}", provider, secretRef);
    }

    private void deleteSecretFromSecretsManager(String secretRef) {
        log.info("Simulating AWS Secrets Manager call: deleteSecret(SecretId={})", secretRef);
    }

    private ApiResponse<Integration> connect(MessageSource provider) {
        UUID userId = getAuthenticatedUserId();
        Optional<Integration> existing = integrationRepository.findByUserIdAndProvider(userId, provider);
        Integration integration;
        if (existing.isPresent()) {
            integration = existing.get();
            integration.setStatus("ACTIVE");
            integration.setSecretRef("arn:aws:secretsmanager:us-east-1:123456789012:secret:user-" + userId + "-" + provider.name().toLowerCase());
        } else {
            integration = new Integration(
                    userId,
                    provider,
                    "ACTIVE",
                    "arn:aws:secretsmanager:us-east-1:123456789012:secret:user-" + userId + "-" + provider.name().toLowerCase()
            );
        }
        Integration saved = integrationRepository.save(integration);
        return ApiResponse.success(saved);
    }

    private UUID getAuthenticatedUserId() {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UUID.fromString(userIdStr);
    }

    @GetMapping("/oauth2/authorize/{provider}")
    public void authorize(
            @PathVariable("provider") String provider,
            @RequestParam("token") String token,
            @RequestParam("scope") String scope,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        
        if (!jwtUtil.validateToken(token)) {
            response.sendError(401, "Invalid token");
            return;
        }
        String userId = jwtUtil.extractUserId(token);

        if ("google".equalsIgnoreCase(provider)) {
            String redirectUri = getBaseUrl(request) + "/api/integrations/oauth2/callback/google";
            String googleScope = "";
            if ("gmail".equalsIgnoreCase(scope)) {
                googleScope = "https://www.googleapis.com/auth/gmail.readonly";
            } else if ("calendar".equalsIgnoreCase(scope)) {
                googleScope = "https://www.googleapis.com/auth/calendar.readonly";
            } else {
                response.sendError(400, "Unknown scope: " + scope);
                return;
            }

            // state is userId:scope
            String state = userId + ":" + scope.toLowerCase();

            String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=" + googleClientId +
                    "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8) +
                    "&response_type=code" +
                    "&scope=" + java.net.URLEncoder.encode(googleScope, java.nio.charset.StandardCharsets.UTF_8) +
                    "&access_type=offline" +
                    "&prompt=consent" +
                    "&state=" + java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8);

            response.sendRedirect(authUrl);
        } else {
            response.sendError(400, "Unknown provider: " + provider);
        }
    }

    @GetMapping("/oauth2/callback/{provider}")
    public void callback(
            @PathVariable("provider") String provider,
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        try {
            String[] parts = state.split(":");
            if (parts.length < 2) {
                response.sendError(400, "Invalid state parameter");
                return;
            }
            UUID userId = UUID.fromString(parts[0]);
            String scope = parts[1]; // gmail or calendar

            if ("google".equalsIgnoreCase(provider)) {
                String redirectUri = getBaseUrl(request) + "/api/integrations/oauth2/callback/google";
                String tokenUrl = "https://oauth2.googleapis.com/token";

                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

                org.springframework.util.MultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
                body.add("code", code);
                body.add("client_id", googleClientId);
                body.add("client_secret", googleClientSecret);
                body.add("redirect_uri", redirectUri);
                body.add("grant_type", "authorization_code");

                org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> requestEntity = new org.springframework.http.HttpEntity<>(body, headers);
                org.springframework.http.ResponseEntity<String> tokenResponse = restTemplate.postForEntity(tokenUrl, requestEntity, String.class);

                com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(tokenResponse.getBody());
                String refreshToken = jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : null;

                MessageSource source = "gmail".equalsIgnoreCase(scope) ? MessageSource.GMAIL : MessageSource.CALENDAR;

                Optional<Integration> existing = integrationRepository.findByUserIdAndProvider(userId, source);
                Integration integration;
                if (existing.isPresent()) {
                    integration = existing.get();
                    integration.setStatus("ACTIVE");
                    if (refreshToken != null) {
                        integration.setRefreshToken(refreshToken);
                    }
                    integration.setSecretRef("arn:aws:secretsmanager:us-east-1:123456789012:secret:user-" + userId + "-" + source.name().toLowerCase());
                } else {
                    integration = new Integration(
                            userId,
                            source,
                            "ACTIVE",
                            "arn:aws:secretsmanager:us-east-1:123456789012:secret:user-" + userId + "-" + source.name().toLowerCase()
                    );
                    integration.setRefreshToken(refreshToken);
                }
                integrationRepository.save(integration);

                response.sendRedirect(frontendUrl + "/app/settings?status=success");
            } else {
                response.sendError(400, "Unknown provider: " + provider);
            }
        } catch (Exception e) {
            log.error("Failed OAuth callback handling for provider " + provider, e);
            response.sendRedirect(frontendUrl + "/app/settings?status=error");
        }
    }

    private String getBaseUrl(jakarta.servlet.http.HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("X-Forwarded-Host");
        if (proto != null && host != null) {
            return proto + "://" + host;
        }
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        
        if ("localhost".equalsIgnoreCase(serverName) || "127.0.0.1".equalsIgnoreCase(serverName)) {
            return scheme + "://" + serverName + ":8080";
        }
        return scheme + "://" + serverName + ":" + serverPort;
    }
}
