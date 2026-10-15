package com.pulse.notification.service;

import com.pulse.api.dto.NotificationPage;
import com.pulse.api.dto.NotificationRequest;
import com.pulse.api.dto.NotificationResponse;
import com.pulse.api.exception.JobNotFoundException;
import com.pulse.domain.ApiKey;
import com.pulse.domain.NotificationJob;
import com.pulse.domain.NotificationStatus;
import com.pulse.notification.publisher.NotificationEventPublisher;
import com.pulse.repository.NotificationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int MAX_PAGE_SIZE = 100;

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

        return toResponse(saved);
    }

    public NotificationResponse getJob(UUID id) {
        NotificationJob job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        return toResponse(job);
    }

    /**
     * Lists the jobs created with the caller's API key, newest first,
     * optionally filtered by status. Page size is capped at {@value #MAX_PAGE_SIZE}.
     */
    @Transactional(readOnly = true)
    public NotificationPage listJobs(ApiKey apiKey, NotificationStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<NotificationJob> result = status == null
                ? jobRepository.findByApiKey(apiKey, pageable)
                : jobRepository.findByApiKeyAndStatus(apiKey, status, pageable);

        return new NotificationPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private NotificationResponse toResponse(NotificationJob job) {
        return NotificationResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .channels(job.getChannels())
                .subject(job.getSubject())
                .recipientEmail(job.getRecipientEmail())
                .recipientPhone(job.getRecipientPhone())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
