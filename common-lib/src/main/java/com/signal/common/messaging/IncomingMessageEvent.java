package com.signal.common.messaging;

import com.signal.common.dto.MessageSource;
import java.time.Instant;
import java.util.UUID;

public record IncomingMessageEvent(
    UUID messageId,
    UUID userId,
    MessageSource source,
    String senderId,
    String senderName,
    String subjectOrPreview,
    String fullContentRef,
    String threadExternalId,
    Instant receivedAt
) {}
