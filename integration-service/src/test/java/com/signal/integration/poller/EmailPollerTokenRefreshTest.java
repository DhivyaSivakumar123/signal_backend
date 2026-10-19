package com.signal.integration.poller;

import com.signal.integration.repository.IntegrationRepository;
import com.signal.integration.service.PollerCoordinatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class EmailPollerTokenRefreshTest {

    @Mock
    private IntegrationRepository integrationRepository;

    @Mock
    private PollerCoordinatorService pollerCoordinatorService;

    private EmailPoller emailPoller;
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        emailPoller = new EmailPoller(
                integrationRepository,
                pollerCoordinatorService,
                "test-client-id",
                "test-client-secret"
        );
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        emailPoller.setRestTemplate(restTemplate);
    }

    @Test
    @DisplayName("refreshAccessToken should return new access token when Google OAuth2 endpoint returns 200 OK")
    void testRefreshAccessTokenSuccess() {
        String mockResponse = "{\"access_token\": \"ya29.mock_new_access_token_12345\", \"expires_in\": 3599, \"token_type\": \"Bearer\"}";

        mockServer.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        String accessToken = emailPoller.refreshAccessToken("1//valid_refresh_token");

        assertNotNull(accessToken);
        assertEquals("ya29.mock_new_access_token_12345", accessToken);
        mockServer.verify();
    }

    @Test
    @DisplayName("refreshAccessToken should return null when Google OAuth2 endpoint returns 400 Bad Request (expired/revoked token)")
    void testRefreshAccessTokenRevokedOrExpired() {
        String errorResponse = "{\"error\": \"invalid_grant\", \"error_description\": \"Token has been expired or revoked.\"}";

        mockServer.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(errorResponse).contentType(MediaType.APPLICATION_JSON));

        String accessToken = emailPoller.refreshAccessToken("1//expired_refresh_token");

        assertNull(accessToken);
        mockServer.verify();
    }

    @Test
    @DisplayName("refreshAccessToken should return null when server responds with 500 error or network exception")
    void testRefreshAccessTokenServerError() {
        mockServer.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        String accessToken = emailPoller.refreshAccessToken("1//valid_refresh_token");

        assertNull(accessToken);
        mockServer.verify();
    }
}
