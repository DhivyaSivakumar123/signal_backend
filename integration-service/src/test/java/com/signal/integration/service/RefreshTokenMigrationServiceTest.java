package com.signal.integration.service;

import com.signal.common.dto.MessageSource;
import com.signal.integration.entity.Integration;
import com.signal.integration.repository.IntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenMigrationServiceTest {

    @Mock
    private IntegrationRepository integrationRepository;

    @InjectMocks
    private RefreshTokenMigrationService migrationService;

    @Test
    @DisplayName("migratePlaintextTokens re-saves all integrations that contain refresh tokens")
    void testMigratePlaintextTokens() {
        Integration integration1 = new Integration(UUID.randomUUID(), MessageSource.GMAIL, "ACTIVE", "secret-ref-1");
        integration1.setRefreshToken("legacy_plaintext_token_1");

        Integration integration2 = new Integration(UUID.randomUUID(), MessageSource.OUTLOOK, "ACTIVE", "secret-ref-2");
        integration2.setRefreshToken("legacy_plaintext_token_2");

        Integration integrationWithoutToken = new Integration(UUID.randomUUID(), MessageSource.SLACK, "ACTIVE", "secret-ref-3");
        integrationWithoutToken.setRefreshToken(null);

        when(integrationRepository.findAll()).thenReturn(List.of(integration1, integration2, integrationWithoutToken));
        when(integrationRepository.save(any(Integration.class))).thenAnswer(inv -> inv.getArgument(0));

        int migrated = migrationService.migratePlaintextTokens();

        assertEquals(2, migrated, "Only integrations with non-null refresh tokens should be migrated and re-saved");
        verify(integrationRepository, times(2)).save(any(Integration.class));
    }
}
