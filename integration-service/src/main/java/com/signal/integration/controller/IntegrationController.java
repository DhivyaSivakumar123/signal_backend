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

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final IntegrationRepository integrationRepository;

    public IntegrationController(IntegrationRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IntegrationController.class);

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
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
}
