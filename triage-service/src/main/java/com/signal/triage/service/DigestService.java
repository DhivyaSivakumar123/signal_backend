package com.signal.triage.service;

import com.signal.triage.entity.TriageItem;
import com.signal.triage.entity.UserPreference;
import com.signal.triage.repository.UserPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final UserPreferenceRepository userPreferenceRepository;
    private final RankingService rankingService;

    public DigestService(UserPreferenceRepository userPreferenceRepository, RankingService rankingService) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.rankingService = rankingService;
    }

    public void sendDigest(UUID userId) {
        log.info("Composing digest for user: {}", userId);
        
        UserPreference preference = userPreferenceRepository.findById(userId)
                .orElse(new UserPreference(userId, "", null, "NONE"));

        if ("NONE".equalsIgnoreCase(preference.getDigestChannel())) {
            log.info("User {} has digest delivery disabled.", userId);
            return;
        }

        List<TriageItem> rankedFeed = rankingService.getRankedFeed(userId);
        if (rankedFeed.isEmpty()) {
            log.info("No items in feed for user {}. Skipping digest.", userId);
            return;
        }

        // Compose digest text
        StringBuilder digest = new StringBuilder();
        digest.append("==== SIGNAL DAILY DIGEST ====\n");
        digest.append("Stated priorities: ").append(preference.getStatedPriorities()).append("\n\n");
        digest.append("Your top action items:\n");
        
        for (TriageItem item : rankedFeed) {
            digest.append(String.format("- [%s] from %s: %s (Reason: %s)\n",
                    item.getUrgency(), item.getSource(), item.getSource(), item.getReasonSnippet()));
        }
        
        digest.append("=============================");

        log.info("[DIGEST DISPATCHED via {} to user {}]:\n{}", 
                preference.getDigestChannel(), userId, digest.toString());
    }
}
