package com.pulse.domain;

/**
 * Outcome of a single {@link DeliveryAttempt}, as opposed to the overall
 * {@link NotificationStatus} of the job.
 * <ul>
 *   <li>{@code DELIVERED}: the provider accepted this attempt.</li>
 *   <li>{@code FAILED}: this attempt failed; the job may still be retried.</li>
 *   <li>{@code RETRYING}: reserved for an attempt that is being retried.</li>
 * </ul>
 */
public enum DeliveryStatus {
    DELIVERED, FAILED, RETRYING
}
