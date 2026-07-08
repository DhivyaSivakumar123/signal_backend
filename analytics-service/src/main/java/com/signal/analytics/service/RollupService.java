package com.signal.analytics.service;

import com.signal.common.dto.MessageSource;
import com.signal.analytics.entity.InterruptionMetric;
import com.signal.analytics.repository.InterruptionMetricRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class RollupService {

    private final InterruptionMetricRepository repository;

    public RollupService(InterruptionMetricRepository repository) {
        this.repository = repository;
    }

    public record TeamLoadRecord(MessageSource source, int hourBucket, long totalCount) {}

    public List<TeamLoadRecord> getTeamLoad(UUID managerId, int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        List<InterruptionMetric> metrics = repository.findByManagerIdAndDateAfter(managerId, since);

        Map<String, Long> groupMap = new HashMap<>();
        for (InterruptionMetric m : metrics) {
            String key = m.getSource().name() + ":" + m.getHourBucket();
            groupMap.put(key, groupMap.getOrDefault(key, 0L) + m.getCount());
        }

        List<TeamLoadRecord> results = new ArrayList<>();
        for (Map.Entry<String, Long> entry : groupMap.entrySet()) {
            String[] split = entry.getKey().split(":");
            MessageSource src = MessageSource.valueOf(split[0]);
            int hour = Integer.parseInt(split[1]);
            results.add(new TeamLoadRecord(src, hour, entry.getValue()));
        }

        return results;
    }
}
