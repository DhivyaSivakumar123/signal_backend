package com.signal.scoring.llm;

import com.signal.common.dto.Urgency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "signal.scoring.provider", havingValue = "mock", matchIfMissing = true)
public class MockUrgencyScorer implements UrgencyScorer {

    @Override
    public UrgencyResult score(ScoringContext context) {
        String text = context.messageText() != null ? context.messageText().toLowerCase() : "";
        
        if (text.contains("immediate") || text.contains("blocking") || text.contains("98%") || text.contains("99%")) {
            return new UrgencyResult(Urgency.NOW, "mock-scorer: detected system blocking indicator");
        }
        if (text.contains("asap") || text.contains("roadmap") || text.contains("planning") || text.contains("eod") || text.contains("sync")) {
            return new UrgencyResult(Urgency.TODAY, "mock-scorer: task or planning roadmap request");
        }
        if (text.contains("vulnerability") || text.contains("security")) {
            return new UrgencyResult(Urgency.THIS_WEEK, "mock-scorer: non-blocking security notification");
        }
        return new UrgencyResult(Urgency.FYI, "mock-scorer: informational message default");
    }
}
