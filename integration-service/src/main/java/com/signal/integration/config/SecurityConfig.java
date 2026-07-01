package com.signal.integration.config;

import com.signal.common.security.JwtUtil;
import com.signal.common.security.SecurityConfigBase;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends SecurityConfigBase {

    public SecurityConfig(JwtUtil jwtUtil) {
        super(jwtUtil);
    }

    @Override
    protected String[] getPublicPaths() {
        return new String[]{
                "/actuator/health"
        };
    }
}
