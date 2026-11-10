package com.pulse.api.dto;

import com.pulse.domain.Channel;
import com.pulse.domain.NotificationStatus;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API view of a notification job, returned when a job is created or fetched.
 * <p>
 * Built from a {@code NotificationJob} entity; the message body and
 * idempotency key are not included.
 */
@Getter
@Builder
public class NotificationResponse {
    private UUID jobId;
    private NotificationStatus status;
    private List<Channel> channels;
    private String subject;
    private String recipientEmail;
    private String recipientPhone;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
