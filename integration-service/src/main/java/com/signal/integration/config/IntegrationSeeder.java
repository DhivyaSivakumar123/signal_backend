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
        // Mock integration seeding disabled
    }
}
