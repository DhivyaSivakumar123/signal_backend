package com.signal.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String googleRedirectUri;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-id}")
    private String msClientId;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-secret}")
    private String msClientSecret;

    @Value("${spring.security.oauth2.client.registration.microsoft.redirect-uri}")
    private String msRedirectUri;

    @Value("${signal.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public String getAuthorizationUrl(String provider, String baseUrl) {
        if ("google".equalsIgnoreCase(provider)) {
            if (googleClientId.startsWith("dummy-")) {
                // Redirect to frontend OAuth callback with dummy code and provider as state
                return frontendUrl + "/oauth-callback?code=dummy-code&state=google";
            }
            String redirectUri = googleRedirectUri.replace("{baseUrl}", baseUrl);
            return "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=" + googleClientId +
                    "&redirect_uri=" + redirectUri +
                    "&response_type=code" +
                    "&scope=openid%20email%20profile";
        } else if ("microsoft".equalsIgnoreCase(provider)) {
            if (msClientId.startsWith("dummy-")) {
                // Redirect to frontend OAuth callback with dummy code and provider as state
                return frontendUrl + "/oauth-callback?code=dummy-code&state=microsoft";
            }
            String redirectUri = msRedirectUri.replace("{baseUrl}", baseUrl);
            return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize" +
                    "?client_id=" + msClientId +
                    "&redirect_uri=" + redirectUri +
                    "&response_type=code" +
                    "&scope=openid%20email%20profile%20User.Read";
        }
        throw new IllegalArgumentException("Unknown provider: " + provider);
    }

    public Map<String, String> getUserProfile(String provider, String code, String baseUrl) {
        if ("dummy-code".equals(code)) {
            log.info("Bypassing external OAuth exchange for dummy-code");
            return Map.of(
                    "email", "user@example.com",
                    "name", "OAuth User",
                    "provider", provider.toUpperCase()
            );
        }

        if ("google".equalsIgnoreCase(provider)) {
            return exchangeGoogleCode(code, baseUrl);
        } else if ("microsoft".equalsIgnoreCase(provider)) {
            return exchangeMicrosoftCode(code, baseUrl);
        }
        throw new IllegalArgumentException("Unknown provider: " + provider);
    }

    private Map<String, String> exchangeGoogleCode(String code, String baseUrl) {
        try {
            String tokenUrl = "https://oauth2.googleapis.com/token";
            String redirectUri = googleRedirectUri.replace("{baseUrl}", baseUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", code);
            body.add("client_id", googleClientId);
            body.add("client_secret", googleClientSecret);
            body.add("redirect_uri", redirectUri);
            body.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, requestEntity, String.class);

            JsonNode tokenResponse = objectMapper.readTree(response.getBody());
            String accessToken = tokenResponse.get("access_token").asText();

            // Get user info
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(accessToken);
            HttpEntity<Void> userEntity = new HttpEntity<>(userHeaders);
            ResponseEntity<String> userResponse = restTemplate.postForEntity(
                    "https://www.googleapis.com/oauth2/v3/userinfo", userEntity, String.class);

            JsonNode userInfo = objectMapper.readTree(userResponse.getBody());
            String email = userInfo.get("email").asText();
            String name = userInfo.get("name").asText();

            return Map.of("email", email, "name", name, "provider", "GOOGLE");
        } catch (Exception e) {
            log.error("Failed Google OAuth exchange", e);
            throw new RuntimeException("Google authentication failed", e);
        }
    }

    private Map<String, String> exchangeMicrosoftCode(String code, String baseUrl) {
        try {
            String tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
            String redirectUri = msRedirectUri.replace("{baseUrl}", baseUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", code);
            body.add("client_id", msClientId);
            body.add("client_secret", msClientSecret);
            body.add("redirect_uri", redirectUri);
            body.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, requestEntity, String.class);

            JsonNode tokenResponse = objectMapper.readTree(response.getBody());
            String accessToken = tokenResponse.get("access_token").asText();

            // Get user info
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(accessToken);
            HttpEntity<Void> userEntity = new HttpEntity<>(userHeaders);
            ResponseEntity<String> userResponse = restTemplate.exchange(
                    "https://graph.microsoft.com/v1.0/me",
                    org.springframework.http.HttpMethod.GET,
                    userEntity,
                    String.class
            );

            JsonNode userInfo = objectMapper.readTree(userResponse.getBody());
            String email = userInfo.has("mail") ? userInfo.get("mail").asText() : userInfo.get("userPrincipalName").asText();
            String name = userInfo.get("displayName").asText();

            return Map.of("email", email, "name", name, "provider", "MICROSOFT");
        } catch (Exception e) {
            log.error("Failed Microsoft OAuth exchange", e);
            throw new RuntimeException("Microsoft authentication failed", e);
        }
    }
}
