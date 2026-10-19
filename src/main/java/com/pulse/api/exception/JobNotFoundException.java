package com.pulse.api.exception;

import java.util.UUID;

/**
 * Thrown when a notification job ID does not match any stored job.
 * {@link GlobalExceptionHandler} maps it to a 404 response.
 */
public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(UUID id) {
        super("Notification job not found: " + id);
    }
}
