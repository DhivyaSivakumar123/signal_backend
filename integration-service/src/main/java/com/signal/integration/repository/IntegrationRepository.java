package com.signal.integration.repository;

import com.signal.common.dto.MessageSource;
import com.signal.integration.entity.Integration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IntegrationRepository extends JpaRepository<Integration, UUID> {
    List<Integration> findByUserId(UUID userId);
    List<Integration> findByStatus(String status);
    Optional<Integration> findByUserIdAndProvider(UUID userId, MessageSource provider);
}
