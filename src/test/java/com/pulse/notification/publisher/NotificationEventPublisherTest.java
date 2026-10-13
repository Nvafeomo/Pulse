package com.pulse.notification.publisher;

import com.pulse.domain.Channel;
import com.pulse.domain.NotificationJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventPublisherTest {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:pulse-notifications";

    @Mock
    private SnsClient snsClient;

    private NotificationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationEventPublisher(snsClient);
        ReflectionTestUtils.setField(publisher, "topicArn", TOPIC_ARN);
    }

    @Test
    void publish_tagsMessageWithChannelForSnsFilterPolicies() {
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("msg-1").build());

        String messageId = publisher.publish(job("Hello"), Channel.SMS);

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        PublishRequest sent = captor.getValue();

        assertThat(messageId).isEqualTo("msg-1");
        assertThat(sent.topicArn()).isEqualTo(TOPIC_ARN);
        assertThat(sent.messageAttributes().get("channel").stringValue()).isEqualTo("SMS");
    }

    @Test
    void buildPayload_staysValidJsonWithQuotesAndNewlines() {
        String body = "He said \"hi\"\nThen left \\ quietly";
        NotificationJob job = job(body);

        JsonNode payload = JsonMapper.builder().build()
                .readTree(publisher.buildPayload(job, Channel.EMAIL));

        assertThat(payload.get("body").asText()).isEqualTo(body);
        assertThat(payload.get("jobId").asText()).isEqualTo(job.getId().toString());
        assertThat(payload.get("channel").asText()).isEqualTo("EMAIL");
    }

    @Test
    void buildPayload_usesEmptyStringsForMissingOptionalFields() {
        NotificationJob job = job("Body only");
        job.setSubject(null);
        job.setRecipientPhone(null);

        JsonNode payload = JsonMapper.builder().build()
                .readTree(publisher.buildPayload(job, Channel.EMAIL));

        assertThat(payload.get("subject").asText()).isEmpty();
        assertThat(payload.get("recipientPhone").asText()).isEmpty();
    }

    private NotificationJob job(String body) {
        NotificationJob job = new NotificationJob();
        job.setId(UUID.randomUUID());
        job.setBody(body);
        job.setSubject("Subject");
        job.setRecipientEmail("user@example.com");
        job.setRecipientPhone("+15555550100");
        return job;
    }
}
