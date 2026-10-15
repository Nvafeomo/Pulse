package com.pulse.api.controller;

import com.pulse.api.dto.NotificationPage;
import com.pulse.api.dto.NotificationResponse;
import com.pulse.api.exception.GlobalExceptionHandler;
import com.pulse.api.exception.JobNotFoundException;
import com.pulse.auth.ApiKeyAuthFilter;
import com.pulse.domain.ApiKey;
import com.pulse.domain.Channel;
import com.pulse.domain.NotificationStatus;
import com.pulse.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mvc;

    private final ApiKey apiKey = new ApiKey();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_passesFiltersAndReturnsPage() throws Exception {
        NotificationResponse job = NotificationResponse.builder()
                .jobId(UUID.randomUUID())
                .status(NotificationStatus.DELIVERED)
                .channels(List.of(Channel.EMAIL))
                .build();
        when(notificationService.listJobs(apiKey, NotificationStatus.DELIVERED, 2, 5))
                .thenReturn(new NotificationPage(List.of(job), 2, 5, 11, 3));

        mvc.perform(get("/api/v1/notifications")
                        .param("status", "DELIVERED")
                        .param("page", "2")
                        .param("size", "5")
                        .requestAttr(ApiKeyAuthFilter.API_KEY_ATTRIBUTE, apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].jobId").value(job.getJobId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void list_usesDefaultPaging() throws Exception {
        when(notificationService.listJobs(apiKey, null, 0, 20))
                .thenReturn(new NotificationPage(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/v1/notifications")
                        .requestAttr(ApiKeyAuthFilter.API_KEY_ATTRIBUTE, apiKey))
                .andExpect(status().isOk());

        verify(notificationService).listJobs(apiKey, null, 0, 20);
    }

    @Test
    void create_returnsAcceptedForTheAuthenticatedKey() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(notificationService.createJob(any(), eq(apiKey))).thenReturn(NotificationResponse.builder()
                .jobId(jobId)
                .status(NotificationStatus.PROCESSING)
                .channels(List.of(Channel.EMAIL))
                .build());

        mvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channels": ["EMAIL"], "body": "Hello", "recipientEmail": "a@example.com"}
                                """)
                        .requestAttr(ApiKeyAuthFilter.API_KEY_ATTRIBUTE, apiKey))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void create_rejectsRequestWithoutBody() throws Exception {
        mvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channels": ["EMAIL"], "body": ""}
                                """)
                        .requestAttr(ApiKeyAuthFilter.API_KEY_ATTRIBUTE, apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.body").exists());

        verifyNoInteractions(notificationService);
    }

    @Test
    void getById_returns404ForUnknownJob() throws Exception {
        UUID missing = UUID.randomUUID();
        when(notificationService.getJob(missing)).thenThrow(new JobNotFoundException(missing));

        mvc.perform(get("/api/v1/notifications/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
