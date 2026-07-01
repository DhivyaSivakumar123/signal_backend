package com.signal.integration.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MockIntegrationClient {

    public record MockMessage(
            String senderId,
            String senderName,
            String subjectOrPreview,
            String fullContent,
            String threadExternalId
    ) {}

    public record MockCalendarEvent(
            String title,
            Instant start,
            Instant end,
            boolean isUpdate,
            String threadExternalId
    ) {}

    public List<MockMessage> getSlackMessages() {
        return List.of(
                new MockMessage(
                        "slack_user_1",
                        "John Doe",
                        "Hey, are you blocking the release? Let me know ASAP!",
                        "Hey, are you blocking the release? Let me know ASAP! We need to push the build by noon.",
                        "slack_thread_1"
                ),
                new MockMessage(
                        "slack_user_2",
                        "Alice Smith",
                        "Good morning! Just a reminder that we have tea time today at 4pm.",
                        "Good morning! Just a reminder that we have tea time today at 4pm in the main cafeteria.",
                        "slack_thread_2"
                ),
                new MockMessage(
                        "slack_manager_1",
                        "Jane Manager", // Manager name matching fallback/dev config
                        "Please review the slides for the Q3 planning deck by EOD today.",
                        "Hi, please review the slides for the Q3 planning deck by EOD today. Let me know if you see any gaps.",
                        "slack_thread_3"
                ),
                new MockMessage(
                        "slack_bot_1",
                        "AlertBot",
                        "ALERT: prod-db-1 CPU usage is at 98%",
                        "ALERT: prod-db-1 CPU usage is at 98%. Immediate action may be required.",
                        "slack_thread_4"
                )
        );
    }

    public List<MockMessage> getEmails() {
        return List.of(
                new MockMessage(
                        "notifications@github.com",
                        "GitHub Notifications",
                        "[GitHub] Security Alert: dependency vulnerability in spring-core",
                        "GitHub detected a vulnerable dependency in signal-backend. Please upgrade as soon as possible.",
                        "email_thread_1"
                ),
                new MockMessage(
                        "manager@acme.com",
                        "Jane Manager",
                        "Quick sync on Q3 roadmap",
                        "Hey, can we catch up for 15 minutes today to discuss the Q3 roadmap? I want to make sure we are aligned.",
                        "email_thread_2"
                ),
                new MockMessage(
                        "spam@deals.com",
                        "Super Deals",
                        "Get 50% off on all workspace monitors!",
                        "Buy our monitors now at half price! Limited offer.",
                        "email_thread_3"
                )
        );
    }

    public List<MockCalendarEvent> getCalendarEvents() {
        Instant now = Instant.now();
        return List.of(
                new MockCalendarEvent(
                        "Q3 Planning Roadmap Review",
                        now.plus(1, ChronoUnit.HOURS),
                        now.plus(2, ChronoUnit.HOURS),
                        false,
                        "cal_event_1"
                ),
                new MockCalendarEvent(
                        "1-on-1 with Jane Manager",
                        now.plus(3, ChronoUnit.HOURS),
                        now.plus(4, ChronoUnit.HOURS),
                        false,
                        "cal_event_2"
                ),
                // Calendar update with no changes
                new MockCalendarEvent(
                        "Weekly Tea Time (Updated Room)",
                        now.plus(5, ChronoUnit.HOURS),
                        now.plus(6, ChronoUnit.HOURS),
                        true,
                        "cal_event_3"
                )
        );
    }
}
