package com.pulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Builds the AWS SDK clients for both environments Pulse runs in.
 *
 * <ul>
 *   <li><b>Local (LocalStack):</b> {@code aws.endpoint} is set, so clients point at
 *       that endpoint and use the static test credentials from application.properties.</li>
 *   <li><b>AWS (ECS):</b> {@code aws.endpoint} is blank, so clients use the real AWS
 *       endpoints and the default credentials chain, which picks up the ECS task role.</li>
 * </ul>
 */
@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.endpoint:}")
    private String endpoint;

    @Value("${aws.accessKeyId:}")
    private String accessKeyId;

    @Value("${aws.secretKey:}")
    private String secretKey;

    @Bean
    public SnsClient snsClient() {
        return configure(SnsClient.builder()).build();
    }

    @Bean
    public SqsClient sqsClient() {
        return configure(SqsClient.builder()).build();
    }

    private <B extends AwsClientBuilder<B, ?>> B configure(B builder) {
        builder.region(Region.of(region));

        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
            if (StringUtils.hasText(accessKeyId) && StringUtils.hasText(secretKey)) {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretKey)));
            }
        }
        // With no endpoint override, the SDK falls back to its default
        // credentials chain (environment, ECS task role, instance profile).
        return builder;
    }
}
