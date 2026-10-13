package com.pulse.notification.publisher;

import com.pulse.domain.Channel;
import com.pulse.domain.NotificationJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    // Serializing with Jackson (instead of a formatted string) keeps the
    // payload valid when a subject or body contains quotes, backslashes,
    // or newlines.
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SnsClient snsClient;

    @Value("${aws.sns.topicArn}")
    private String topicArn;

    public String publish(NotificationJob job, Channel channel) {
        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(buildPayload(job, channel))
                .messageAttributes(Map.of("channel", MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(channel.name())
                        .build()))
                .build();

        PublishResponse response = snsClient.publish(request);
        log.info("Published to SNS: jobId={} channel={} messageId={}",
                job.getId(), channel, response.messageId());
        return response.messageId();
    }

    String buildPayload(NotificationJob job, Channel channel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", String.valueOf(job.getId()));
        payload.put("channel", channel.name());
        payload.put("recipientEmail", nullSafe(job.getRecipientEmail()));
        payload.put("recipientPhone", nullSafe(job.getRecipientPhone()));
        payload.put("subject", nullSafe(job.getSubject()));
        payload.put("body", job.getBody());
        return JSON.writeValueAsString(payload);
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
