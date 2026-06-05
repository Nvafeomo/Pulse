package com.pulse.repository;

import com.pulse.domain.NotificationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, UUID> {
}
