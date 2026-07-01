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
public class CalendarPoller {

    private static final Logger log = LoggerFactory.getLogger(CalendarPoller.class);

    private final IntegrationRepository integrationRepository;
    private final MockIntegrationClient mockIntegrationClient;
    private final PollerCoordinatorService pollerCoordinatorService;

    public CalendarPoller(IntegrationRepository integrationRepository,
                          MockIntegrationClient mockIntegrationClient,
                          PollerCoordinatorService pollerCoordinatorService) {
        this.integrationRepository = integrationRepository;
        this.mockIntegrationClient = mockIntegrationClient;
        this.pollerCoordinatorService = pollerCoordinatorService;
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
            log.info("Polling Calendar for user: {}", integration.getUserId());
            try {
                // Stagger/jitter execution to prevent thundering herd
                Thread.sleep(50 + new java.util.Random().nextInt(150));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<MockIntegrationClient.MockCalendarEvent> events = mockIntegrationClient.getCalendarEvents();
            for (MockIntegrationClient.MockCalendarEvent event : events) {
                // Publish Calendar Events as IncomingMessageEvent too, so they can be scored!
                String detail = "Start: " + event.start() + ", End: " + event.end() + ", Updated: " + event.isUpdate();
                pollerCoordinatorService.processIncomingMessage(
                        integration.getUserId(),
                        MessageSource.CALENDAR,
                        event.threadExternalId(),
                        "calendar_system",
                        "Calendar System",
                        event.title(),
                        detail
                );
            }
            integration.setLastSyncedAt(Instant.now());
            integrationRepository.save(integration);
        }
    }
}
