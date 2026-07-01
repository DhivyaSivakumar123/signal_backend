package com.signal.scoring.rules;

import com.signal.common.dto.Urgency;
import com.signal.scoring.llm.ScoringContext;
import com.signal.scoring.llm.UrgencyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class PreFilterRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(PreFilterRuleEngine.class);

    private static final Pattern AUTOMATED_SENDER_PATTERN = Pattern.compile(
            "(?i).*(noreply|no-reply|notification|alert|bot|spam|deals|info|marketing)@.*|calendar_system"
    );

    private static final Pattern URGENT_KEYWORDS_PATTERN = Pattern.compile(
            "(?i).*\\b(urgent|asap|blocked|blocking|eod|by today|need this now)\\b.*"
    );

    public Optional<UrgencyResult> evaluate(ScoringContext context) {
        // Rule 1: Sender is known no-reply/automated
        if (context.senderEmail() != null && AUTOMATED_SENDER_PATTERN.matcher(context.senderEmail()).matches()) {
            log.info("Rule 1 Matched: Automated sender. Resolving as FYI.");
            return Optional.of(new UrgencyResult(Urgency.FYI, "auto-resolved: automated sender"));
        }
        if ("calendar_system".equalsIgnoreCase(context.senderEmail()) || "Calendar System".equalsIgnoreCase(context.senderName())) {
            log.info("Rule 1/2 Matched: Calendar system. Resolving as FYI.");
            return Optional.of(new UrgencyResult(Urgency.FYI, "auto-resolved: calendar system update"));
        }

        // Rule 2: Message content indicates calendar event update with no agenda change
        if (context.channelOrContext().toLowerCase().contains("calendar") && 
            context.messageText() != null && context.messageText().contains("Updated Room")) {
            log.info("Rule 2 Matched: Calendar update with no agenda change. Resolving as FYI.");
            return Optional.of(new UrgencyResult(Urgency.FYI, "auto-resolved: calendar update (no agenda change)"));
        }

        // Rule 4: Explicit urgency keywords present -> Must go to LLM, cannot auto-resolve to FYI
        if (context.messageText() != null && URGENT_KEYWORDS_PATTERN.matcher(context.messageText()).matches()) {
            log.info("Rule 4 Matched: Explicit urgency keywords. Forcing LLM call.");
            return Optional.empty(); // Fall through to LLM
        }

        return Optional.empty(); // Fall through to LLM
    }
}
