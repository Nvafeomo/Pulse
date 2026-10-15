package com.pulse.api.controller;

import com.pulse.api.dto.NotificationPage;
import com.pulse.api.dto.NotificationRequest;
import com.pulse.api.dto.NotificationResponse;
import com.pulse.auth.ApiKeyAuthFilter;
import com.pulse.domain.ApiKey;
import com.pulse.domain.NotificationStatus;
import com.pulse.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationResponse create(@Valid @RequestBody NotificationRequest request,
                                       @RequestAttribute(ApiKeyAuthFilter.API_KEY_ATTRIBUTE) ApiKey apiKey) {
        return notificationService.createJob(request, apiKey);
    }

    @GetMapping
    public NotificationPage list(@RequestAttribute(ApiKeyAuthFilter.API_KEY_ATTRIBUTE) ApiKey apiKey,
                                 @RequestParam(required = false) NotificationStatus status,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return notificationService.listJobs(apiKey, status, page, size);
    }

    @GetMapping("/{id}")
    public NotificationResponse getById(@PathVariable UUID id) {
        return notificationService.getJob(id);
    }
}
