package com.signal.scoring.llm;

public record ScoringContext(
    String messageText,
    String senderName,
    String senderEmail,
    String senderRelationship, // DIRECT_REPORT|PEER|MANAGER|SKIP_LEVEL|EXTERNAL|UNKNOWN
    String channelOrContext,   // e.g. "Slack DM", "#general", "Direct Email"
    String threadHistorySummary,
    String statedPriorities
) {}
