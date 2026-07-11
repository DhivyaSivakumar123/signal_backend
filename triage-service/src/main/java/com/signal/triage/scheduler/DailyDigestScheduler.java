package com.signal.triage.scheduler;

import com.signal.triage.entity.UserPreference;
import com.signal.triage.repository.UserPreferenceRepository;
import com.signal.triage.service.DigestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class DailyDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestScheduler.class);

    private final UserPreferenceRepository userPreferenceRepository;
    private final DigestService digestService;

    public DailyDigestScheduler(UserPreferenceRepository userPreferenceRepository, DigestService digestService) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.digestService = digestService;
    }

    @Scheduled(cron = "0 * * * * *") // Runs every minute
    public void checkAndSendDigests() {
        log.info("Running minute digest scheduler check...");
        String tz = System.getenv().getOrDefault("TZ", "Asia/Kolkata");
        ZoneId zoneId = ZoneId.of(tz);
        LocalTime now = LocalTime.now(zoneId);
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();
        LocalDate today = LocalDate.now(zoneId);
        
        List<UserPreference> preferences = userPreferenceRepository.findAll();
        for (UserPreference pref : preferences) {
            if (pref.getDigestTime() != null) {
                LocalTime digestTime = pref.getDigestTime();
                if (digestTime.getHour() == currentHour && digestTime.getMinute() == currentMinute) {
                    if (pref.getLastDigestSentAt() == null || !pref.getLastDigestSentAt().equals(today)) {
                        try {
                            digestService.sendDigest(pref.getUserId());
                            pref.setLastDigestSentAt(today);
                            userPreferenceRepository.save(pref);
                            log.info("Successfully dispatched scheduled digest for user: {}", pref.getUserId());
                        } catch (Exception e) {
                            log.error("Failed to send scheduled digest for user: {}", pref.getUserId(), e);
                        }
                    } else {
                        log.info("Digest already sent today for user: {}", pref.getUserId());
                    }
                }
            }
        }
    }
}
