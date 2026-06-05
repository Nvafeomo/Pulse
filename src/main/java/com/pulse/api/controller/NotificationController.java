package com.pulse.api.controller;

import com.pulse.api.dto.NotificationRequest;
import com.pulse.api.dto.NotificationResponse;
import com.pulse.domain.ApiKey;
import com.pulse.notification.service.NotificationService;
import com.pulse.repository.ApiKeyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final ApiKeyRepository apiKeyRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationResponse create(@Valid @RequestBody NotificationRequest request,
                                       HttpServletRequest httpRequest) {
        String rawKey = httpRequest.getHeader("X-API-Key");
        ApiKey apiKey = apiKeyRepository
                .findByKeyHashAndActiveTrue(sha256(rawKey))
                .orElseThrow();

        return notificationService.createJob(request, apiKey);
    }

    @GetMapping("/{id}")
    public NotificationResponse getById(@PathVariable UUID id) {
        return notificationService.getJob(id);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
