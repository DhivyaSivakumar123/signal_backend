package com.signal.integration.service;

import com.signal.integration.crypto.AesGcmEncryptionConverter;
import com.signal.integration.entity.Integration;
import com.signal.integration.repository.IntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Migration service to ensure all legacy plaintext refresh tokens in the database
 * are transparently migrated and re-encrypted using AES-256-GCM (v1:ENC: format).
 */
@Service
public class RefreshTokenMigrationService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenMigrationService.class);

    private final IntegrationRepository integrationRepository;

    public RefreshTokenMigrationService(IntegrationRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
    }

    /**
     * Scans all integrations in the database, re-saving any entity that contains
     * a non-null refresh token. The JPA AttributeConverter (AesGcmEncryptionConverter)
     * automatically converts and encrypts legacy plaintext or old-format tokens to v1:ENC:.
     *
     * @return Number of migrated/re-saved integrations.
     */
    @Transactional
    public int migratePlaintextTokens() {
        log.info("Starting refresh token migration scan for unencrypted/legacy rows...");
        List<Integration> all = integrationRepository.findAll();
        int migratedCount = 0;

        for (Integration integration : all) {
            String token = integration.getRefreshToken();
            if (token != null && !token.isBlank()) {
                // Setting and saving triggers the JPA converter to re-encrypt into current v1:ENC: format
                integration.setRefreshToken(token);
                integrationRepository.save(integration);
                migratedCount++;
            }
        }

        log.info("Completed refresh token migration. Successfully processed {} integrations.", migratedCount);
        return migratedCount;
    }
}
