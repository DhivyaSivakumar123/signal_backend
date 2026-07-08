package com.signal.scoring.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signal.common.dto.Urgency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Urgency scorer backed by Groq's API (llama-3.1-8b-instant).
 * Groq's chat completions endpoint is OpenAI-compatible, so the request/response
 * structure is identical — only the base URL, model name, and API key differ.
 */
@Service
@ConditionalOnProperty(name = "signal.scoring.provider", havingValue = "groq")
public class GroqUrgencyScorer implements UrgencyScorer {

    private static final Logger log = LoggerFactory.getLogger(GroqUrgencyScorer.class);
    private static final String GROQ_API_URL  = "https://api.groq.com/openai/v1";
    private static final String GROQ_MODEL    = "llama-3.1-8b-instant";

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GroqUrgencyScorer(RestClient.Builder restClientBuilder,
                              @Value("${signal.scoring.groq.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(GROQ_API_URL).build();
        this.apiKey = apiKey;
    }

    @Override
    public UrgencyResult score(ScoringContext context) {
        log.info("Scoring message with Groq ({})", GROQ_MODEL);

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Groq API Key is missing");
        }

        try {
            String systemPrompt =
                    "You are an assistant judging urgency for a busy manager. " +
                    "Respond with valid JSON only — no markdown fences, no extra text:\n" +
                    "{ \"urgency\": \"NOW\" | \"TODAY\" | \"THIS_WEEK\" | \"FYI\", " +
                    "\"reason\": \"<one sentence, under 20 words>\" }";

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
                    context.threadHistorySummary()  != null ? context.threadHistorySummary()  : "",
                    context.statedPriorities()      != null ? context.statedPriorities()      : ""
            );

            // Groq's /chat/completions is fully OpenAI-compatible.
            // Note: Groq does NOT yet support response_format: json_object for all models,
            // so we rely on the system prompt + cleanJsonString() instead.
            Map<String, Object> requestBody = Map.of(
                    "model",       GROQ_MODEL,
                    "temperature", 1,
                    "max_tokens",  1024,
                    "top_p",       1,
                    "stream",      false,   // streaming not needed for synchronous scoring
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user",   "content", userPrompt)
                    )
            );

            String responseJson = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root    = objectMapper.readTree(responseJson);
            String   content = root.path("choices").get(0)
                                   .path("message").path("content").asText();

            content = cleanJsonString(content);

            JsonNode parsedResult = objectMapper.readTree(content);
            String   urgencyStr   = parsedResult.path("urgency").asText("THIS_WEEK").toUpperCase();
            String   reason       = parsedResult.path("reason").asText("auto-fallback: reason missing");

            Urgency urgency = Urgency.valueOf(urgencyStr);
            log.info("Groq scored urgency={} reason={}", urgency, reason);
            return new UrgencyResult(urgency, reason);

        } catch (Exception e) {
            log.error("Groq scoring failed or failed to parse JSON response.", e);
            throw new RuntimeException("Groq scoring failed", e);
        }
    }

    private String cleanJsonString(String input) {
        if (input == null) return "";
        return input.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*",     "")
                .replaceAll("\\s*```$",     "")
                .trim();
    }
}
