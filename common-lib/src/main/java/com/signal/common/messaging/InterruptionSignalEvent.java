package com.signal.common.messaging;

import com.signal.common.dto.MessageSource;
import java.time.Instant;
import java.util.UUID;

/**
 * Stripped-down interruption event containing NO text or message content fields to enforce privacy.
 */
public record InterruptionSignalEvent(
    UUID orgId,
    UUID managerId,
    String teamMemberHash,
    MessageSource source,
    Instant occurredAt
) {}
