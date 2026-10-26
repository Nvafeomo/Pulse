package com.pulse.domain;

/**
 * Delivery channel a notification can be sent through. Each channel a job
 * requests is published as its own SNS message, tagged with a
 * {@code channel} attribute that routes it to that channel's SQS queue.
 * <ul>
 *   <li>{@code EMAIL}: delivered by the email consumer through SES.</li>
 *   <li>{@code SMS}: routed to the SMS queue; a Twilio consumer is planned.</li>
 * </ul>
 */
public enum Channel {
    EMAIL, SMS
}
