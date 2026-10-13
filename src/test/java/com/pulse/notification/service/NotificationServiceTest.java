package com.pulse.notification.service;

import com.pulse.api.dto.NotificationRequest;
import com.pulse.api.dto.NotificationResponse;
import com.pulse.api.exception.JobNotFoundException;
import com.pulse.domain.ApiKey;
import com.pulse.domain.Channel;
import com.pulse.domain.NotificationJob;
import com.pulse.domain.NotificationStatus;
import com.pulse.notification.publisher.NotificationEventPublisher;
import com.pulse.repository.NotificationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationJobRepository jobRepository;

    @Mock
    private NotificationEventPublisher publisher;

    @InjectMocks
    private NotificationService service;

    private final ApiKey apiKey = new ApiKey();

    @BeforeEach
    void setUp() {
        // Mimic JPA assigning an id on first save.
        lenient().when(jobRepository.save(any(NotificationJob.class))).thenAnswer(invocation -> {
            NotificationJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(UUID.randomUUID());
            }
            return job;
        });
        lenient().when(publisher.publish(any(), any())).thenReturn("sns-message-id");
    }

    @Test
    void createJob_publishesOneEventPerChannel() {
        NotificationRequest request = request(List.of(Channel.EMAIL, Channel.SMS));

        service.createJob(request, apiKey);

        verify(publisher).publish(any(NotificationJob.class), eq(Channel.EMAIL));
        verify(publisher).publish(any(NotificationJob.class), eq(Channel.SMS));
        verify(publisher, times(2)).publish(any(), any());
    }

    @Test
    void createJob_marksJobProcessingAfterPublishing() {
        NotificationResponse response = service.createJob(request(List.of(Channel.EMAIL)), apiKey);

        assertThat(response.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(response.getJobId()).isNotNull();
        assertThat(response.getChannels()).containsExactly(Channel.EMAIL);
    }

    @Test
    void createJob_usesClientIdempotencyKeyWhenProvided() {
        NotificationRequest request = request(List.of(Channel.EMAIL));
        request.setIdempotencyKey("order-1234-receipt");

        service.createJob(request, apiKey);

        ArgumentCaptor<NotificationJob> captor = ArgumentCaptor.forClass(NotificationJob.class);
        verify(jobRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("order-1234-receipt");
    }

    @Test
    void createJob_generatesIdempotencyKeyWhenMissing() {
        service.createJob(request(List.of(Channel.EMAIL)), apiKey);

        ArgumentCaptor<NotificationJob> captor = ArgumentCaptor.forClass(NotificationJob.class);
        verify(jobRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isNotBlank();
    }

    @Test
    void getJob_returnsStoredJob() {
        NotificationJob job = new NotificationJob();
        job.setId(UUID.randomUUID());
        job.setChannels(List.of(Channel.SMS));
        job.setStatus(NotificationStatus.DELIVERED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        NotificationResponse response = service.getJob(job.getId());

        assertThat(response.getJobId()).isEqualTo(job.getId());
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void getJob_throwsWhenJobDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(jobRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(missing))
                .isInstanceOf(JobNotFoundException.class);
    }

    private NotificationRequest request(List<Channel> channels) {
        NotificationRequest request = new NotificationRequest();
        request.setChannels(channels);
        request.setBody("Your order has shipped");
        request.setSubject("Order update");
        request.setRecipientEmail("user@example.com");
        request.setRecipientPhone("+15555550100");
        return request;
    }
}
