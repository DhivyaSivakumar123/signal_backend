package com.signal.triage.repository;

import com.signal.triage.entity.SenderUrgencyBias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SenderUrgencyBiasRepository extends JpaRepository<SenderUrgencyBias, UUID> {
    Optional<SenderUrgencyBias> findByUserIdAndSenderId(UUID userId, String senderId);
}
