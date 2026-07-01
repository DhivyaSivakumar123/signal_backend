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

    public EmailPoller(IntegrationRepository integrationRepository,
                       MockIntegrationClient mockIntegrationClient,
                       PollerCoordinatorService pollerCoordinatorService) {
        this.integrationRepository = integrationRepository;
        this.mockIntegrationClient = mockIntegrationClient;
        this.pollerCoordinatorService = pollerCoordinatorService;
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
            List<MockIntegrationClient.MockMessage> emails = mockIntegrationClient.getEmails();
            for (MockIntegrationClient.MockMessage email : emails) {
                // Ensure we process it as either GMAIL or OUTLOOK depending on provider
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
            integration.setLastSyncedAt(Instant.now());
            integrationRepository.save(integration);
        }
    }
}
