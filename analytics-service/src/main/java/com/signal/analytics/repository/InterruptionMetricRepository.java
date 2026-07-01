package com.signal.analytics.repository;

import com.signal.common.dto.MessageSource;
import com.signal.analytics.entity.InterruptionMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterruptionMetricRepository extends JpaRepository<InterruptionMetric, UUID> {
    
    Optional<InterruptionMetric> findByOrgIdAndManagerIdAndTeamMemberHashAndSourceAndHourBucketAndDate(
            UUID orgId, UUID managerId, String teamMemberHash, MessageSource source, int hourBucket, LocalDate date);

    List<InterruptionMetric> findByManagerIdAndDateAfter(UUID managerId, LocalDate since);
}
