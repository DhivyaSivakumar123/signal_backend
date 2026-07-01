package com.signal.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signal.auth.entity.Org;
import com.signal.auth.entity.User;
import com.signal.auth.repository.OrgRepository;
import com.signal.auth.repository.UserRepository;
import com.signal.common.dto.ApiResponse;
import com.signal.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;INIT=CREATE SCHEMA IF NOT EXISTS auth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.default_schema="
})
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgRepository orgRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();
        orgRepository.deleteAll();
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    public void testDevLoginAndMeFlow() throws Exception {
        // 1. Trigger dev-login
        String loginPayload = """
                {
                    "email": "jane@acme.com",
                    "name": "Jane Doe",
                    "role": "IC",
                    "managerEmail": ""
                }
                """;

        String responseContent = mockMvc.perform(post("/api/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.userId", notNullValue()))
                .andExpect(jsonPath("$.data.orgId", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        ApiResponse<?> response = objectMapper.readValue(responseContent, ApiResponse.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> data = (java.util.Map<String, String>) response.data();
        String accessToken = data.get("accessToken");

        // 2. Query /api/me with valid token
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email", is("jane@acme.com")))
                .andExpect(jsonPath("$.data.name", is("Jane Doe")))
                .andExpect(jsonPath("$.data.role", is("IC")));
    }

    @Test
    public void testMeUnauthorized() throws Exception {
        // Query /api/me without a token -> must return 401 in standard envelope
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data", nullValue()))
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    public void testMeBadToken() throws Exception {
        // Query /api/me with invalid token -> must return 401
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer bad_token_value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data", nullValue()))
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    public void testTokenRefreshAndLogout() throws Exception {
        // 1. Pre-insert an org and user
        Org org = orgRepository.save(new Org("ACME", "acme.com"));
        User user = userRepository.save(new User("john@acme.com", "John", org.getId(), "IC", null, "DEV"));

        String refreshToken = jwtUtil.generateRefreshToken(user.getId().toString());

        // Setup mock Redis check to return false (not blacklisted)
        Mockito.when(redisTemplate.hasKey("blacklist:" + refreshToken)).thenReturn(false);

        // 2. Perform refresh
        String refreshPayload = String.format("{\"refreshToken\":\"%s\"}", refreshToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));

        // 3. Perform logout (requires valid access token)
        String accessToken = jwtUtil.generateAccessToken(user.getId().toString(), org.getId().toString(), user.getRole(), user.getEmail());
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isOk());

        // Verify blacklist write call was made
        Mockito.verify(valueOperations).set(Mockito.eq("blacklist:" + refreshToken), Mockito.anyString(), any(java.time.Duration.class));
    }
}
