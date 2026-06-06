package com.pulse.notification.service;

import com.pulse.api.dto.NotificationRequest;
import com.pulse.api.dto.NotificationResponse;
import com.pulse.api.exception.JobNotFoundException;
import com.pulse.domain.ApiKey;
import com.pulse.domain.NotificationJob;
import com.pulse.domain.NotificationStatus;
import com.pulse.notification.publisher.NotificationEventPublisher;
import com.pulse.repository.NotificationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationJobRepository jobRepository;
    private final NotificationEventPublisher publisher;

    @Transactional
    public NotificationResponse createJob(NotificationRequest request, ApiKey apiKey) {
        NotificationJob job = new NotificationJob();
        job.setApiKey(apiKey);
        job.setChannels(request.getChannels());
        job.setBody(request.getBody());
        job.setSubject(request.getSubject());
        job.setRecipientEmail(request.getRecipientEmail());
        job.setRecipientPhone(request.getRecipientPhone());
        job.setIdempotencyKey(
            request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : UUID.randomUUID().toString()
        );

        NotificationJob saved = jobRepository.save(job);

        // Publish one SNS message per channel
        request.getChannels().forEach(channel -> {
            String messageId = publisher.publish(saved, channel);
            saved.setSnsMessageId(messageId);
        });

        saved.setStatus(NotificationStatus.PROCESSING);
        jobRepository.save(saved);

        return NotificationResponse.builder()
                .jobId(saved.getId())
                .status(saved.getStatus())
                .channels(saved.getChannels())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public NotificationResponse getJob(UUID id) {
        NotificationJob job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        return NotificationResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .channels(job.getChannels())
                .createdAt(job.getCreatedAt())
                .build();
    }
}
