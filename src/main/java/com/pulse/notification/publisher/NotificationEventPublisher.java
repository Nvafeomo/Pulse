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

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final SnsClient snsClient;

    @Value("${aws.sns.topicArn}")
    private String topicArn;

    public String publish(NotificationJob job, Channel channel) {
        String messageBody = """
                {
                  "jobId": "%s",
                  "channel": "%s",
                  "recipientEmail": "%s",
                  "recipientPhone": "%s",
                  "subject": "%s",
                  "body": "%s"
                }
                """.formatted(
                job.getId(),
                channel.name(),
                nullSafe(job.getRecipientEmail()),
                nullSafe(job.getRecipientPhone()),
                nullSafe(job.getSubject()),
                job.getBody()
        );

        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put("channel", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(channel.name())
                .build());

        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(messageBody)
                .messageAttributes(attributes)
                .build();

        PublishResponse response = snsClient.publish(request);
        log.info("Published to SNS: jobId={} channel={} messageId={}",
                job.getId(), channel, response.messageId());
        return response.messageId();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
