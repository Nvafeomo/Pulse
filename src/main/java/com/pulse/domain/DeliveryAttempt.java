package com.pulse.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
@Getter @Setter
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private NotificationJob job;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "channel")
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "delivery_status")
    private DeliveryStatus status;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 1;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "provider_message_id")
    private String providerMessageId;
}
