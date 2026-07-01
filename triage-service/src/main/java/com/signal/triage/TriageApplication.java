package com.signal.triage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.signal.common", "com.signal.triage"})
@EnableJpaAuditing
@EnableScheduling
public class TriageApplication {
    public static void main(String[] args) {
        SpringApplication.run(TriageApplication.class, args);
    }
}
