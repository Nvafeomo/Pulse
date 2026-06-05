package com.pulse.notification.service;

import com.pulse.api.dto.NotificationRequest;
import com.pulse.api.dto.NotificationResponse;
import com.pulse.domain.ApiKey;
import com.pulse.domain.NotificationJob;
import com.pulse.repository.NotificationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationJobRepository jobRepository;

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

        return NotificationResponse.builder()
                .jobId(saved.getId())
                .status(saved.getStatus())
                .channels(saved.getChannels())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}
