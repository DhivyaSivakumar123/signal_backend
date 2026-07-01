package com.signal.analytics.service;

import com.signal.common.dto.MessageSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RecommendationService {

    private final RollupService rollupService;

    public RecommendationService(RollupService rollupService) {
        this.rollupService = rollupService;
    }

    public List<String> getRecommendations(UUID managerId) {
        List<RollupService.TeamLoadRecord> load = rollupService.getTeamLoad(managerId);
        List<String> recommendations = new ArrayList<>();

        if (load.isEmpty()) {
            recommendations.add("Not enough historical data to generate load recommendations. Let the pollers run to aggregate signals.");
            return recommendations;
        }

        long totalCount = 0;
        long slackCount = 0;
        long morningCount = 0; // 9:00 - 12:00 (hours 9, 10, 11)

        for (RollupService.TeamLoadRecord record : load) {
            totalCount += record.totalCount();
            if (record.source() == MessageSource.SLACK) {
                slackCount += record.totalCount();
            }
            if (record.hourBucket() >= 9 && record.hourBucket() <= 11) {
                morningCount += record.totalCount();
            }
        }

        if (totalCount > 0) {
            double slackPercentage = (double) slackCount / totalCount;
            if (slackPercentage > 0.60) {
                recommendations.add(String.format(
                        "Slack accounts for %.1f%% of team interruptions. Recommend establishing guidelines to batch notifications and discourage instant-reply expectations.",
                        slackPercentage * 100
                ));
            }
            
            double morningPercentage = (double) morningCount / totalCount;
            if (morningPercentage > 0.40) {
                recommendations.add("High morning load detected (9 AM - 12 PM). Consider setting a daily 'No-Meeting/Quiet Work Block' between 10:00 and 11:30 to promote deep work.");
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Team interruption patterns look healthy. Keep up the clean async communication practices!");
        }

        return recommendations;
    }
}
