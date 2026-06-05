package com.pulse.api.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(UUID id) {
        super("Notification job not found: " + id);
    }
}
