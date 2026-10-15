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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.pulse.api.dto.NotificationPage;

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

    @Test
    void listJobs_filtersByStatusAndSortsNewestFirst() {
        NotificationJob job = new NotificationJob();
        job.setId(UUID.randomUUID());
        job.setChannels(List.of(Channel.EMAIL));
        job.setStatus(NotificationStatus.FAILED);
        Pageable expected = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationJob> page = new PageImpl<>(List.of(job), expected, 11);
        when(jobRepository.findByApiKeyAndStatus(apiKey, NotificationStatus.FAILED, expected)).thenReturn(page);

        NotificationPage result = service.listJobs(apiKey, NotificationStatus.FAILED, 1, 10);

        assertThat(result.content()).extracting(NotificationResponse::getJobId).containsExactly(job.getId());
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(11);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void listJobs_withoutStatusReturnsAllJobsForKey() {
        when(jobRepository.findByApiKey(eq(apiKey), any(Pageable.class))).thenReturn(Page.empty());

        NotificationPage result = service.listJobs(apiKey, null, 0, 20);

        assertThat(result.content()).isEmpty();
        verify(jobRepository).findByApiKey(eq(apiKey), any(Pageable.class));
    }

    @Test
    void listJobs_capsPageSizeAndClampsNegativePage() {
        when(jobRepository.findByApiKey(eq(apiKey), any(Pageable.class))).thenReturn(Page.empty());

        service.listJobs(apiKey, null, -3, 5_000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(jobRepository).findByApiKey(eq(apiKey), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
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
