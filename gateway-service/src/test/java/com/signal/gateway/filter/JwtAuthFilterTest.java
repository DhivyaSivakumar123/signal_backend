package com.signal.gateway.filter;

import com.signal.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtil);
    }

    @Test
    @DisplayName("Public path should bypass JWT authentication")
    void testPublicPathBypassesAuthentication() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/dev-login").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);
        result.block();

        verify(chain).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Denied: Protected path without Authorization header returns 401 Unauthorized")
    void testMissingAuthHeaderReturns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/triage/feed").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);
        result.block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Denied: Protected path with invalid/tampered token returns 401 Unauthorized")
    void testInvalidTokenReturns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/triage/feed")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-or-expired-token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        when(jwtUtil.validateToken("invalid-or-expired-token")).thenReturn(false);

        Mono<Void> result = filter.filter(exchange, chain);
        result.block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Denied: Protected path with malformed header prefix returns 401 Unauthorized")
    void testMalformedHeaderReturns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/triage/feed")
                .header(HttpHeaders.AUTHORIZATION, "Basic invalid-scheme")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);
        result.block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Allowed: Valid token extracts and forwards X-User-Id and X-User-Role (ROLE_ADMIN)")
    void testAllowedWithRoleAdminPropagated() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/triage/feed")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-admin-token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.validateToken("valid-admin-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-admin-token")).thenReturn("user-uuid-admin-123");
        when(jwtUtil.extractRole("valid-admin-token")).thenReturn("ROLE_ADMIN");
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            assertEquals("user-uuid-admin-123", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            assertEquals("ROLE_ADMIN", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Role"));
            return Mono.empty();
        });

        Mono<Void> result = filter.filter(exchange, chain);
        result.block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Allowed: Valid token extracts and forwards X-User-Id and X-User-Role (ROLE_MEMBER)")
    void testAllowedWithRoleMemberPropagated() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/integrations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-member-token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.validateToken("valid-member-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-member-token")).thenReturn("user-uuid-member-456");
        when(jwtUtil.extractRole("valid-member-token")).thenReturn("ROLE_MEMBER");
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            assertEquals("user-uuid-member-456", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            assertEquals("ROLE_MEMBER", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Role"));
            return Mono.empty();
        });

        Mono<Void> result = filter.filter(exchange, chain);
        result.block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Allowed: Valid token without role forwards only X-User-Id")
    void testValidTokenWithoutRoleForwardsOnlyUserId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/triage/feed")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn("user-uuid-789");
        when(jwtUtil.extractRole("valid-token")).thenReturn(null);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            assertEquals("user-uuid-789", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            assertNull(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Role"));
            return Mono.empty();
        });

        Mono<Void> result = filter.filter(exchange, chain);
        result.block();

        verify(chain).filter(any(ServerWebExchange.class));
    }
}
