package com.signal.integration.config;

import com.signal.common.dto.MessageSource;
import com.signal.integration.entity.Integration;
import com.signal.integration.repository.IntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IntegrationSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IntegrationSeeder.class);

    private final IntegrationRepository integrationRepository;

    public IntegrationSeeder(IntegrationRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        UUID devUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        
        if (integrationRepository.findByUserId(devUserId).isEmpty()) {
            log.info("Seeding default integrations for developer userId: {}", devUserId);
            
            integrationRepository.save(new Integration(devUserId, MessageSource.SLACK, "ACTIVE", "mock-arn-slack"));
            integrationRepository.save(new Integration(devUserId, MessageSource.GMAIL, "ACTIVE", "mock-arn-gmail"));
            integrationRepository.save(new Integration(devUserId, MessageSource.OUTLOOK, "ACTIVE", "mock-arn-outlook"));
            integrationRepository.save(new Integration(devUserId, MessageSource.CALENDAR, "ACTIVE", "mock-arn-calendar"));
            
            log.info("Default integrations seeded successfully.");
        }
    }
}
