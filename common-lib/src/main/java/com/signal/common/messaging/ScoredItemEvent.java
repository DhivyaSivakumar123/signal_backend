package com.signal.common.messaging;

import com.signal.common.dto.MessageSource;
import com.signal.common.dto.Urgency;
import java.time.Instant;
import java.util.UUID;

public record ScoredItemEvent(
    UUID scoredItemId,
    UUID userId,
    UUID threadGroupId,
    Urgency urgency,
    String reasonSnippet,
    MessageSource source,
    String senderId,
    Instant receivedAt
) {}
