package com.pulse.repository;

import com.pulse.domain.ApiKey;
import com.pulse.domain.NotificationJob;
import com.pulse.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Spring Data repository for {@link NotificationJob} records.
 */
public interface NotificationJobRepository extends JpaRepository<NotificationJob, UUID> {

    /**
     * Returns a page of notification jobs submitted with the given API key.
     *
     * @param apiKey   the API key that submitted the jobs
     * @param pageable paging and sorting parameters
     * @return a page of matching jobs
     */
    Page<NotificationJob> findByApiKey(ApiKey apiKey, Pageable pageable);

    /**
     * Returns a page of notification jobs submitted with the given API key
     * that are currently in the given status.
     *
     * @param apiKey   the API key that submitted the jobs
     * @param status   the job status to filter by
     * @param pageable paging and sorting parameters
     * @return a page of matching jobs
     */
    Page<NotificationJob> findByApiKeyAndStatus(ApiKey apiKey, NotificationStatus status, Pageable pageable);
}
