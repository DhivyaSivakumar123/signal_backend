package com.signal.auth.repository;

import com.signal.auth.entity.Org;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrgRepository extends JpaRepository<Org, UUID> {
    Optional<Org> findByDomain(String domain);
}
