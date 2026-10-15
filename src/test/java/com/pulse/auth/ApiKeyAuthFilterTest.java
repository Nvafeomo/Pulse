package com.pulse.auth;

import com.pulse.domain.ApiKey;
import com.pulse.repository.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    // SHA-256 of "pulse-dev-key-local", the documented local dev key.
    private static final String DEV_KEY = "pulse-dev-key-local";
    private static final String DEV_KEY_HASH =
            "ad2be3b1edd1a3e7837e53b8db58da0ca4e54e82c328e1cf863dff824268cc35";

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyAuthFilter filter;

    @Test
    void rejectsRequestWithoutApiKey() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/notifications"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void rejectsUnknownApiKey() throws Exception {
        when(apiKeyRepository.findByKeyHashAndActiveTrue(anyString())).thenReturn(Optional.empty());
        MockHttpServletRequest request = request("/api/v1/notifications");
        request.addHeader("X-API-Key", "not-a-real-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        verify(apiKeyRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptsValidKeyAndRecordsLastUse() throws Exception {
        ApiKey key = new ApiKey();
        when(apiKeyRepository.findByKeyHashAndActiveTrue(DEV_KEY_HASH)).thenReturn(Optional.of(key));
        MockHttpServletRequest request = request("/api/v1/notifications");
        request.addHeader("X-API-Key", DEV_KEY);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(key.getLastUsedAt()).isNotNull();
        assertThat(request.getAttribute(ApiKeyAuthFilter.API_KEY_ATTRIBUTE)).isSameAs(key);
        verify(apiKeyRepository).save(key);
    }

    @Test
    void letsCorsPreflightThroughWithoutApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/notifications");
        request.setRequestURI("/api/v1/notifications");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void skipsAuthForActuatorEndpoints() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/actuator/health"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(apiKeyRepository);
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
