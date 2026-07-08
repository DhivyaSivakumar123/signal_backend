package com.signal.auth.controller;

import com.signal.auth.service.AuthService;
import com.signal.auth.service.OAuthService;
import com.signal.common.dto.ApiResponse;
import com.signal.common.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping
public class AuthController {

    private final AuthService authService;
    private final OAuthService oAuthService;
    private final String frontendUrl;

    public AuthController(AuthService authService,
                          OAuthService oAuthService,
                          @Value("${signal.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.authService = authService;
        this.oAuthService = oAuthService;
        this.frontendUrl = frontendUrl;
    }

    @PostMapping("/api/auth/dev-login")
    public ApiResponse<Map<String, String>> devLogin(@RequestBody DevLoginRequest request) {
        Map<String, String> tokens = authService.devLogin(
                request.email(), request.name(), request.role(), request.managerEmail());
        return ApiResponse.success(tokens);
    }

    @GetMapping("/api/me")
    public ApiResponse<UserDTO> me() {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserDTO user = authService.getUserProfile(userId);
        return ApiResponse.success(user);
    }

    @GetMapping("/api/auth/users/{id}")
    public ApiResponse<UserDTO> getUserById(@PathVariable("id") String id) {
        UserDTO user = authService.getUserProfile(id);
        return ApiResponse.success(user);
    }

    @PostMapping("/api/auth/refresh")
    public ApiResponse<Map<String, String>> refresh(@RequestBody RefreshRequest request) {
        Map<String, String> tokens = authService.refreshAccessToken(request.refreshToken());
        return ApiResponse.success(tokens);
    }

    @PostMapping("/api/auth/logout")
    public ApiResponse<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success(null);
    }

    @GetMapping("/oauth2/authorize/{provider}")
    public void authorize(@PathVariable("provider") String provider, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String baseUrl = getBaseUrl(request);
        String authUrl = oAuthService.getAuthorizationUrl(provider, baseUrl);
        logRedirect(provider, authUrl);
        response.sendRedirect(authUrl);
    }

    @GetMapping("/oauth2/callback/{provider}")
    public void callback(
            @PathVariable("provider") String provider,
            @RequestParam("code") String code,
            HttpServletResponse response) throws IOException {
        response.sendRedirect(frontendUrl + "/oauth-callback?code=" + code + "&state=" + provider);
    }

    @PostMapping("/api/auth/callback")
    public ApiResponse<Map<String, String>> callbackPost(@RequestBody CallbackRequest callbackRequest,
                                                          HttpServletRequest request) {
        String baseUrl = getBaseUrl(request);
        String provider = callbackRequest.state() != null ? callbackRequest.state() : "google";
        Map<String, String> profile = oAuthService.getUserProfile(provider, callbackRequest.code(), baseUrl);
        Map<String, String> tokens = authService.registerOrLoginOAuthUser(
                profile.get("email"),
                profile.get("name"),
                profile.get("provider"),
                profile.get("picture")
        );
        return ApiResponse.success(tokens);
    }

    private String getBaseUrl(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("X-Forwarded-Host");
        if (proto != null && host != null) {
            return proto + "://" + host;
        }
        // Fallback to ServerName and ServerPort, but default to port 8080 in local context if it's localhost
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        
        if ("localhost".equalsIgnoreCase(serverName) || "127.0.0.1".equalsIgnoreCase(serverName)) {
            // Usually Gateway runs on 8080 and routes to auth-service on 8081
            return scheme + "://" + serverName + ":8080";
        }
        return scheme + "://" + serverName + ":" + serverPort;
    }

    private void logRedirect(String provider, String url) {
        org.slf4j.LoggerFactory.getLogger(AuthController.class)
                .info("Redirecting to {} auth URL: {}", provider, url);
    }

    public record DevLoginRequest(String email, String name, String role, String managerEmail) {}
    public record RefreshRequest(String refreshToken) {}
    public record CallbackRequest(String code, String state) {}
}
