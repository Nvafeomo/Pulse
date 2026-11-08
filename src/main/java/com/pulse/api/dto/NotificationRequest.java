package com.pulse.api.dto;

import com.pulse.domain.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * Request body for creating a notification job.
 * <p>
 * {@code channels} and {@code body} are required. When {@code idempotencyKey}
 * is omitted, the service generates a random one for the job.
 */
@Getter @Setter
public class NotificationRequest {

    @NotEmpty(message = "At least one channel is required")
    private List<Channel> channels;

    @NotBlank(message = "Body is required")
    private String body;

    private String subject;
    private String recipientEmail;
    private String recipientPhone;

    @Size(max = 255)
    private String idempotencyKey;
}
