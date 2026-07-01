package com.signal.scoring.repository;

import com.signal.scoring.entity.ThreadGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThreadGroupRepository extends JpaRepository<ThreadGroup, UUID> {
    Optional<ThreadGroup> findByUserIdAndTopicHash(UUID userId, String topicHash);
    Optional<ThreadGroup> findByUserIdAndThreadExternalId(UUID userId, String threadExternalId);
    List<ThreadGroup> findByUserId(UUID userId);
}
