package com.pulse.repository;

import com.pulse.domain.ApiKey;
import com.pulse.domain.NotificationJob;
import com.pulse.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, UUID> {

    Page<NotificationJob> findByApiKey(ApiKey apiKey, Pageable pageable);

    Page<NotificationJob> findByApiKeyAndStatus(ApiKey apiKey, NotificationStatus status, Pageable pageable);
}
