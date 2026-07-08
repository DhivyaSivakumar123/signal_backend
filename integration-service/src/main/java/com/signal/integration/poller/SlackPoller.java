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
public class SlackPoller {

    private static final Logger log = LoggerFactory.getLogger(SlackPoller.class);

    private final IntegrationRepository integrationRepository;
    private final PollerCoordinatorService pollerCoordinatorService;

    public SlackPoller(IntegrationRepository integrationRepository,
                       PollerCoordinatorService pollerCoordinatorService) {
        this.integrationRepository = integrationRepository;
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
        log.info("Slack polling for user: {} is skipped (Mock Slack generation disabled).", integration.getUserId());
        integration.setLastSyncedAt(Instant.now());
        integrationRepository.save(integration);
    }
}
