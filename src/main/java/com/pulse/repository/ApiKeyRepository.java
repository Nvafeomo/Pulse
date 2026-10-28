package com.pulse.repository;

import com.pulse.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link ApiKey} records.
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Looks up an active API key by the SHA-256 hash of its raw value.
     * Keys marked inactive are never returned.
     *
     * @param keyHash hex-encoded SHA-256 hash of the raw API key
     * @return the matching active key, or empty if none exists
     */
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);
}
