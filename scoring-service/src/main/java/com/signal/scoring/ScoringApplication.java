package com.signal.scoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"com.signal.common", "com.signal.scoring"})
@EnableJpaAuditing
public class ScoringApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScoringApplication.class, args);
    }
}
