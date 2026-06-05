package com.pulse.repository;

import com.pulse.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);
}
