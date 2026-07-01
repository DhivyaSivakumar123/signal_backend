package com.signal.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated Redis integration test. 
 * Uses @ContextConfiguration to load an empty config, preventing AuthApplication and JPA from loading.
 * Loads Redis connection configurations dynamically from the workspace's .env file.
 */
@DataRedisTest(excludeAutoConfiguration = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@ContextConfiguration(classes = AuthRedisLogicTest.TestConfig.class)
public class AuthRedisLogicTest {

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
        // Custom configuration context to avoid JpaMappingContext errors
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        try {
            // Try standard relative paths to locate the workspace .env file
            java.io.File envFile = null;
            String[] possiblePaths = {"../.env", ".env", "../../.env", "backend/.env"};
            for (String path : possiblePaths) {
                java.io.File file = new java.io.File(path);
                if (file.exists() && file.isFile()) {
                    envFile = file;
                    break;
                }
            }

            if (envFile != null) {
                System.out.println(">>> AuthRedisLogicTest: Loading Redis credentials from " + envFile.getAbsolutePath());
                java.util.List<String> lines = java.nio.file.Files.readAllLines(envFile.toPath());
                String redisHost = null;
                String redisPort = null;
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        if ("SPRING_DATA_REDIS_HOST".equals(key)) {
                            redisHost = value;
                        } else if ("SPRING_DATA_REDIS_PORT".equals(key)) {
                            redisPort = value;
                        }
                    }
                }
                if (redisHost != null) {
                    if (redisHost.startsWith("redis://") || redisHost.startsWith("rediss://")) {
                        // If host is a full connection URI, map to spring.data.redis.url
                        final String finalUrl = redisHost;
                        registry.add("spring.data.redis.url", () -> finalUrl);
                    } else {
                        final String finalHost = redisHost;
                        registry.add("spring.data.redis.host", () -> finalHost);
                        if (redisPort != null) {
                            final String finalPort = redisPort;
                            registry.add("spring.data.redis.port", () -> finalPort);
                        }
                    }
                }
            } else {
                System.err.println(">>> AuthRedisLogicTest: WARNING: .env file not found. Defaulting to local properties.");
            }
        } catch (Exception e) {
            System.err.println(">>> AuthRedisLogicTest: Failed to parse .env file: " + e.getMessage());
        }
    }

    @Test
    public void testRedisConnectionAndBlacklistLogic() {
        assertNotNull(redisTemplate, "RedisTemplate should be auto-configured");

        String token = "eyJhbGciOiJIUzUxMiJ9.testToken";
        String blacklistKey = "blacklist:" + token;

        try {
            // 1. Write key to Redis with a 5-second TTL (simulate blacklisting)
            redisTemplate.opsForValue().set(blacklistKey, "revoked", Duration.ofSeconds(5));

            // 2. Read and verify the value is written
            String value = redisTemplate.opsForValue().get(blacklistKey);
            assertEquals("revoked", value, "Token should be recorded as revoked in Redis");

            // 3. Verify key existence
            Boolean hasKey = redisTemplate.hasKey(blacklistKey);
            assertTrue(hasKey, "Blacklisted key should exist in Redis");

            // 4. Verify TTL is positive
            Long expire = redisTemplate.getExpire(blacklistKey);
            assertNotNull(expire);
            assertTrue(expire > 0, "TTL should be set and positive");

            // 5. Clean up
            redisTemplate.delete(blacklistKey);
            assertFalse(redisTemplate.hasKey(blacklistKey), "Key should be deleted after cleanup");

            System.out.println(">>> Redis Connection and Blacklist Business Logic test passed successfully!");

        } catch (Exception e) {
            fail("Failed to verify Redis connection or logic. Error: " + e.getMessage());
        }
    }
}
