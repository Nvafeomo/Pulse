package com.pulse.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    void allowsDashboardOriginAndApiKeyHeaderOnApiRoutes() {
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", List.of("http://localhost:4200"));

        CorsConfiguration config = corsConfig.corsConfigurationSource()
                .getCorsConfiguration(request("/api/v1/notifications"));

        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("http://localhost:4200")).isEqualTo("http://localhost:4200");
        assertThat(config.checkOrigin("https://evil.example")).isNull();
        assertThat(config.getAllowedHeaders()).contains("X-API-Key");
    }

    @Test
    void doesNotApplyOutsideApiRoutes() {
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", List.of("http://localhost:4200"));

        assertThat(corsConfig.corsConfigurationSource()
                .getCorsConfiguration(request("/actuator/health"))).isNull();
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
