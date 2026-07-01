package com.signal.triage.repository;

import com.signal.triage.entity.TriageItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TriageItemRepository extends JpaRepository<TriageItem, UUID> {
    List<TriageItem> findByUserIdAndStatusIn(UUID userId, Collection<String> statuses);
    List<TriageItem> findByUserId(UUID userId);
}
