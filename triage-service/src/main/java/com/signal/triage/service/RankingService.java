package com.signal.triage.service;

import com.signal.common.dto.Urgency;
import com.signal.triage.entity.TriageItem;
import com.signal.triage.repository.TriageItemRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RankingService {

    private final TriageItemRepository triageItemRepository;

    public RankingService(TriageItemRepository triageItemRepository) {
        this.triageItemRepository = triageItemRepository;
    }

    public List<TriageItem> getRankedFeed(UUID userId) {
        List<TriageItem> activeItems = triageItemRepository.findByUserIdAndStatusIn(
                userId, List.of("UNSEEN", "SEEN"));

        Map<UUID, TriageItem> collapsedMap = new HashMap<>();
        for (TriageItem item : activeItems) {
            TriageItem existing = collapsedMap.get(item.getThreadGroupId());
            if (existing == null) {
                collapsedMap.put(item.getThreadGroupId(), item);
            } else {
                if (compareUrgency(item.getUrgency(), existing.getUrgency()) > 0) {
                    collapsedMap.put(item.getThreadGroupId(), item);
                } else if (item.getUrgency() == existing.getUrgency() && 
                           item.getReceivedAt().isAfter(existing.getReceivedAt())) {
                    collapsedMap.put(item.getThreadGroupId(), item);
                }
            }
        }

        List<TriageItem> result = new ArrayList<>(collapsedMap.values());
        
        result.sort((a, b) -> {
            int urgencyCompare = compareUrgency(b.getUrgency(), a.getUrgency());
            if (urgencyCompare != 0) {
                return urgencyCompare;
            }
            return b.getReceivedAt().compareTo(a.getReceivedAt());
        });

        return result;
    }

    private int compareUrgency(Urgency u1, Urgency u2) {
        return Integer.compare(getUrgencyWeight(u1), getUrgencyWeight(u2));
    }

    private int getUrgencyWeight(Urgency u) {
        return switch (u) {
            case NOW -> 3;
            case TODAY -> 2;
            case THIS_WEEK -> 1;
            case FYI -> 0;
        };
    }
}
