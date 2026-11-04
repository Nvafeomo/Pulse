package com.pulse.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A notification requested through the API, stored in the
 * {@code notification_jobs} table. Holds the owning {@link ApiKey}, the target
 * {@link Channel}s, the recipient details, the message content, and the
 * current {@link NotificationStatus}. {@code snsMessageId} keeps only the ID of
 * the most recently published SNS message, since one message is published per
 * channel. {@code updatedAt} is refreshed on every update by {@link #onUpdate()}.
 */
@Entity
@Table(name = "notification_jobs")
@Getter @Setter
public class NotificationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id", nullable = false)
    private ApiKey apiKey;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Convert(converter = ChannelListConverter.class)
    @Column(name = "channels", nullable = false)
    private List<Channel> channels;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(name = "sns_message_id")
    private String snsMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
