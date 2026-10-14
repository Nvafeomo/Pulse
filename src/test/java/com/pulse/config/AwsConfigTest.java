package com.pulse.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class AwsConfigTest {

    @Test
    void usesEndpointOverrideWhenEndpointIsSet() {
        AwsConfig config = config("http://localhost:4566");

        try (SnsClient client = config.snsClient()) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("http://localhost:4566"));
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
        }
    }

    @Test
    void usesRealAwsEndpointsWhenEndpointIsBlank() {
        AwsConfig config = config("");

        try (SnsClient client = config.snsClient()) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
        }
    }

    private AwsConfig config(String endpoint) {
        AwsConfig config = new AwsConfig();
        ReflectionTestUtils.setField(config, "region", "us-east-1");
        ReflectionTestUtils.setField(config, "endpoint", endpoint);
        ReflectionTestUtils.setField(config, "accessKeyId", "test");
        ReflectionTestUtils.setField(config, "secretKey", "test");
        return config;
    }
}
