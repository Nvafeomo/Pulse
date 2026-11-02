package com.pulse.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A client credential for the Pulse API, stored in the {@code api_keys} table.
 * Only the SHA-256 hash of the raw key is persisted. Inactive keys are rejected
 * at authentication, and {@code lastUsedAt} is refreshed on each accepted request.
 */
@Entity
@Table(name = "api_keys")
@Getter @Setter
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;
}
