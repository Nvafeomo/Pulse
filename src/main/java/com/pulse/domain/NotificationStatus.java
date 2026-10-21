package com.pulse.domain;

/**
 * Lifecycle state of a {@link NotificationJob}.
 * <ul>
 *   <li>{@code PENDING}: the job has been saved but not yet published.</li>
 *   <li>{@code PROCESSING}: the job has been published and is awaiting delivery.</li>
 *   <li>{@code DELIVERED}: a consumer delivered the notification successfully.</li>
 *   <li>{@code FAILED}: delivery failed and all retries are exhausted.</li>
 * </ul>
 */
public enum NotificationStatus {
    PENDING, PROCESSING, DELIVERED, FAILED
}
