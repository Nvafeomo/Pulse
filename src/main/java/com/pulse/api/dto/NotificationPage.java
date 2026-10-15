package com.pulse.api.dto;

import java.util.List;

/**
 * One page of notification jobs. Kept as a small explicit DTO rather than
 * returning Spring's Page directly, so the JSON shape is stable.
 */
public record NotificationPage(
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
