package com.signal.triage.service;

import com.signal.triage.entity.TriageItem;
import com.signal.triage.entity.UserPreference;
import com.signal.triage.repository.UserPreferenceRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final UserPreferenceRepository userPreferenceRepository;
    private final RankingService rankingService;
    private final EntityManager entityManager;
    private final JavaMailSender mailSender;

    public DigestService(UserPreferenceRepository userPreferenceRepository,
                         RankingService rankingService,
                         EntityManager entityManager,
                         JavaMailSender mailSender) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.rankingService = rankingService;
        this.entityManager = entityManager;
        this.mailSender = mailSender;
    }

    public void sendDigest(UUID userId) {
        log.info("Composing digest for user: {}", userId);
        
        UserPreference preference = userPreferenceRepository.findById(userId)
                .orElse(new UserPreference(userId, "", null, "NONE"));

        if ("NONE".equalsIgnoreCase(preference.getDigestChannel())) {
            log.info("User {} has digest delivery disabled.", userId);
            return;
        }

        List<TriageItem> rankedFeed = rankingService.getRankedFeed(userId);
        if (rankedFeed.isEmpty()) {
            log.info("No items in feed for user {}. Skipping digest.", userId);
            return;
        }

        // Compose digest text
        StringBuilder digest = new StringBuilder();
        digest.append("==== SIGNAL DAILY DIGEST ====\n");
        digest.append("Stated priorities: ").append(preference.getStatedPriorities()).append("\n\n");
        digest.append("Your top action items:\n");
        
        for (TriageItem item : rankedFeed) {
            digest.append(String.format("- [%s] from %s: %s (Reason: %s)\n",
                    item.getUrgency(), item.getSource(), item.getSource(), item.getReasonSnippet()));
        }
        
        digest.append("=============================");

        if ("EMAIL".equalsIgnoreCase(preference.getDigestChannel())) {
            String email = getUserEmail(userId);
            if (email != null && !email.isBlank()) {
                sendRealEmail(email, "Signal Daily Digest", digest.toString());
            } else {
                log.warn("No registered email address found for user: {}", userId);
            }
        } else {
            log.info("[DIGEST DISPATCHED via {} to user {}]:\n{}", 
                    preference.getDigestChannel(), userId, digest.toString());
        }
    }

    private String getUserEmail(UUID userId) {
        try {
            String sql = "SELECT email FROM auth.users WHERE id = :userId";
            List<?> results = entityManager.createNativeQuery(sql)
                    .setParameter("userId", userId)
                    .getResultList();
            if (!results.isEmpty()) {
                return (String) results.get(0);
            }
        } catch (Exception e) {
            log.error("Failed to query user email from auth schema", e);
        }
        return null;
    }

    private void sendRealEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("noreply@signal.com");
            mailSender.send(message);
            log.info("Successfully sent real digest email to: {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send real email via SMTP (make sure your .env has correct mail server settings). Falling back to console simulation log. Error: {}", e.getMessage());
            log.info("[SIMULATED EMAIL DIGEST] to user email {}:\n{}", toEmail, body);
        }
    }
}
