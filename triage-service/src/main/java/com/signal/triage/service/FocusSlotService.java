package com.signal.triage.service;

import com.signal.common.dto.Urgency;
import com.signal.triage.entity.TriageItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FocusSlotService {

    private final RankingService rankingService;

    public FocusSlotService(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    public record FocusSlot(
            Instant startTime,
            Instant endTime,
            long durationMinutes,
            List<TriageItem> recommendedItems
    ) {}

    public List<FocusSlot> getFocusSlots(UUID userId) {
        List<TriageItem> rankedFeed = rankingService.getRankedFeed(userId);
        
        // Filter only NOW and TODAY items
        List<TriageItem> urgentItems = rankedFeed.stream()
                .filter(item -> item.getUrgency() == Urgency.NOW || item.getUrgency() == Urgency.TODAY)
                .toList();

        Instant now = Instant.now();
        
        // Setup two focus slots for today: 
        // Slot 1: in 1 hour, duration 90 minutes
        // Slot 2: in 4 hours, duration 60 minutes
        FocusSlot slot1 = new FocusSlot(
                now.plus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES),
                90,
                new ArrayList<>(urgentItems)
        );

        FocusSlot slot2 = new FocusSlot(
                now.plus(4, ChronoUnit.HOURS),
                now.plus(5, ChronoUnit.HOURS),
                60,
                urgentItems.stream().limit(2).toList() // Recommend top 2 for the shorter slot
        );

        return List.of(slot1, slot2);
    }
}
