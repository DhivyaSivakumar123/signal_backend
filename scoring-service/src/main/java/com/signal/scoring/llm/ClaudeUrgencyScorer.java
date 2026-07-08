package com.signal.scoring.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signal.common.dto.Urgency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "signal.scoring.provider", havingValue = "claude")
public class ClaudeUrgencyScorer implements UrgencyScorer {

    private static final Logger log = LoggerFactory.getLogger(ClaudeUrgencyScorer.class);

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeUrgencyScorer(RestClient.Builder restClientBuilder,
                               @Value("${signal.scoring.claude.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl("https://api.anthropic.com/v1").build();
        this.apiKey = apiKey;
    }

    @Override
    public UrgencyResult score(ScoringContext context) {
        log.info("Scoring message with Anthropic Claude");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Claude API Key is missing");
        }

        try {
            String systemPrompt = "You are an assistant judging urgency for a busy manager. Respond with structured JSON only:\n" +
                    "{ \"urgency\": \"NOW\" | \"TODAY\" | \"THIS_WEEK\" | \"FYI\", \"reason\": \"<one sentence, under 20 words>\" }";

            String userPrompt = String.format(
                    "Message Content: %s\n" +
                    "Sender Name: %s\n" +
                    "Sender Relationship: %s\n" +
                    "Channel/Context: %s\n" +
                    "Thread History Summary: %s\n" +
                    "Stated Priorities: %s\n",
                    context.messageText(),
                    context.senderName(),
                    context.senderRelationship(),
                    context.channelOrContext(),
                    context.threadHistorySummary() != null ? context.threadHistorySummary() : "",
                    context.statedPriorities() != null ? context.statedPriorities() : ""
            );

            Map<String, Object> requestBody = Map.of(
                    "model", "claude-3-5-sonnet-20240620",
                    "max_tokens", 1000,
                    "system", systemPrompt,
                    "messages", List.of(
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            String responseJson = restClient.post()
                    .uri("/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            String content = root.path("content").get(0).path("text").asText();

            content = cleanJsonString(content);

            JsonNode parsedResult = objectMapper.readTree(content);
            String urgencyStr = parsedResult.path("urgency").asText("THIS_WEEK").toUpperCase();
            String reason = parsedResult.path("reason").asText("auto-fallback: reason missing");

            Urgency urgency = Urgency.valueOf(urgencyStr);
            return new UrgencyResult(urgency, reason);

        } catch (Exception e) {
            log.error("Claude scoring failed or failed to parse JSON response.", e);
            throw new RuntimeException("Claude scoring failed", e);
        }
    }

    private String cleanJsonString(String input) {
        if (input == null) return "";
        return input.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
    }
}
