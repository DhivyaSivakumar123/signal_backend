package com.signal.auth.service;

import com.signal.auth.entity.Org;
import com.signal.auth.entity.User;
import com.signal.auth.repository.OrgRepository;
import com.signal.auth.repository.UserRepository;
import com.signal.common.dto.UserDTO;
import com.signal.common.exception.ResourceNotFoundException;
import com.signal.common.security.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrgRepository orgRepository;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public AuthService(UserRepository userRepository, OrgRepository orgRepository,
                       JwtUtil jwtUtil, StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.orgRepository = orgRepository;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Map<String, String> devLogin(String email, String name, String role, String managerEmail) {
        String domain = getDomainFromEmail(email);
        Org org = orgRepository.findByDomain(domain)
                .orElseGet(() -> orgRepository.save(new Org(domain.split("\\.")[0].toUpperCase(), domain)));

        UUID managerId = null;
        if (managerEmail != null && !managerEmail.isBlank()) {
            Optional<User> managerOpt = userRepository.findByEmail(managerEmail);
            if (managerOpt.isPresent()) {
                managerId = managerOpt.get().getId();
            } else {
                User manager = new User(managerEmail, "Manager " + managerEmail.split("@")[0], org.getId(), "MANAGER", null, "DEV");
                manager = userRepository.save(manager);
                managerId = manager.getId();
            }
        }

        final UUID finalManagerId = managerId;
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(email, name, org.getId(), role, finalManagerId, "DEV")));

        // Generate tokens
        String accessToken = jwtUtil.generateAccessToken(user.getId().toString(), org.getId().toString(), user.getRole(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId().toString());

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "userId", user.getId().toString(),
                "orgId", org.getId().toString()
        );
    }

    @Transactional
    public Map<String, String> registerOrLoginOAuthUser(String email, String name, String provider) {
        String domain = getDomainFromEmail(email);
        Org org = orgRepository.findByDomain(domain)
                .orElseGet(() -> orgRepository.save(new Org(domain.split("\\.")[0].toUpperCase(), domain)));

        User user = userRepository.findByEmail(email)
                .map(u -> {
                    if ("IC".equals(u.getRole())) {
                        u.setRole("MANAGER");
                        return userRepository.save(u);
                    }
                    return u;
                })
                .orElseGet(() -> userRepository.save(new User(email, name, org.getId(), "MANAGER", null, provider)));

        String accessToken = jwtUtil.generateAccessToken(user.getId().toString(), org.getId().toString(), user.getRole(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId().toString());

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "userId", user.getId().toString(),
                "orgId", org.getId().toString()
        );
    }

    public UserDTO getUserProfile(String userIdStr) {
        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userIdStr));
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getOrgId(),
                user.getRole(),
                user.getManagerId(),
                user.getOauthProvider()
        );
    }

    public Map<String, String> refreshAccessToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new com.signal.common.exception.UnauthorizedException("Invalid refresh token");
        }

        Boolean isBlacklisted = redisTemplate.hasKey("blacklist:" + refreshToken);
        if (Boolean.TRUE.equals(isBlacklisted)) {
            throw new com.signal.common.exception.UnauthorizedException("Refresh token has been revoked");
        }

        String userIdStr = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(UUID.fromString(userIdStr))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId().toString(), user.getOrgId().toString(), user.getRole(), user.getEmail());

        return Map.of("accessToken", newAccessToken);
    }

    public void logout(String refreshToken) {
        if (jwtUtil.validateToken(refreshToken)) {
            Date expiration = jwtUtil.extractExpiration(refreshToken);
            long ttlMillis = expiration.getTime() - System.currentTimeMillis();
            if (ttlMillis > 0) {
                redisTemplate.opsForValue().set("blacklist:" + refreshToken, "revoked", Duration.ofMillis(ttlMillis));
            }
        }
    }

    private String getDomainFromEmail(String email) {
        int index = email.indexOf("@");
        if (index == -1) {
            throw new IllegalArgumentException("Invalid email format");
        }
        return email.substring(index + 1);
    }
}
