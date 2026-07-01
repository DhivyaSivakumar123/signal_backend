package com.signal.scoring.repository;

import com.signal.scoring.entity.ScoredItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoredItemRepository extends JpaRepository<ScoredItem, UUID> {
    Optional<ScoredItem> findByExternalRef(String externalRef);
    long countByThreadGroupIdAndScoredAtAfter(UUID threadGroupId, Instant since);
}
