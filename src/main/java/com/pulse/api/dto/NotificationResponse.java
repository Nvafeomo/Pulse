package com.pulse.api.dto;

import com.pulse.domain.Channel;
import com.pulse.domain.NotificationStatus;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class NotificationResponse {
    private UUID jobId;
    private NotificationStatus status;
    private List<Channel> channels;
    private OffsetDateTime createdAt;
}
