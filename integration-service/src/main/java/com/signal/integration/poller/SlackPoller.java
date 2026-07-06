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
public class SlackPoller {

    private static final Logger log = LoggerFactory.getLogger(SlackPoller.class);

    private final IntegrationRepository integrationRepository;
    private final MockIntegrationClient mockIntegrationClient;
    private final PollerCoordinatorService pollerCoordinatorService;

    public SlackPoller(IntegrationRepository integrationRepository,
                       MockIntegrationClient mockIntegrationClient,
                       PollerCoordinatorService pollerCoordinatorService) {
        this.integrationRepository = integrationRepository;
        this.mockIntegrationClient = mockIntegrationClient;
        this.pollerCoordinatorService = pollerCoordinatorService;
    }

    @Scheduled(fixedDelay = 120000, initialDelay = 10000)
    public void pollSlack() {
        log.info("Starting Slack integration polling...");
        List<Integration> slackIntegrations = integrationRepository.findByStatus("ACTIVE").stream()
                .filter(i -> i.getProvider() == MessageSource.SLACK)
                .toList();

        if (slackIntegrations.isEmpty()) {
            log.info("No active Slack integrations found.");
            return;
        }

        for (Integration integration : slackIntegrations) {
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
        log.info("Polling Slack for user: {}", integration.getUserId());
        List<MockIntegrationClient.MockMessage> messages = mockIntegrationClient.getSlackMessages();
        for (MockIntegrationClient.MockMessage message : messages) {
            pollerCoordinatorService.processIncomingMessage(
                    integration.getUserId(),
                    MessageSource.SLACK,
                    message.threadExternalId(),
                    message.senderId(),
                    message.senderName(),
                    message.subjectOrPreview(),
                    message.fullContent()
            );
        }
        integration.setLastSyncedAt(Instant.now());
        integrationRepository.save(integration);
    }
}
