package com.signal.scoring.llm;

import com.signal.common.dto.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class GroqUrgencyScorerTest {

    private GroqUrgencyScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new GroqUrgencyScorer(RestClient.builder(), "test-groq-key");
    }

    @Test
    @DisplayName("cleanJsonString should strip ```json and ``` markdown code fences")
    void testCleanJsonStringWithMarkdownFences() {
        String fencedJson = "```json\n{\"urgency\": \"NOW\", \"reason\": \"Critical production outage\"}\n```";
        String cleaned = scorer.cleanJsonString(fencedJson);
        assertEquals("{\"urgency\": \"NOW\", \"reason\": \"Critical production outage\"}", cleaned);
    }

    @Test
    @DisplayName("cleanJsonString should strip generic ``` fences and trim whitespace")
    void testCleanJsonStringWithGenericFences() {
        String fencedJson = "   ``` \n{\"urgency\": \"TODAY\", \"reason\": \"Roadmap sync requested\"}  ```  ";
        String cleaned = scorer.cleanJsonString(fencedJson);
        assertEquals("{\"urgency\": \"TODAY\", \"reason\": \"Roadmap sync requested\"}", cleaned);
    }

    @Test
    @DisplayName("cleanJsonString should return untouched string when no markdown fences exist")
    void testCleanJsonStringPlainJson() {
        String plain = "{\"urgency\": \"THIS_WEEK\", \"reason\": \"Standard review\"}";
        String cleaned = scorer.cleanJsonString(plain);
        assertEquals(plain, cleaned);
    }

    @Test
    @DisplayName("cleanJsonString should handle null or blank input gracefully")
    void testCleanJsonStringNullOrBlank() {
        assertEquals("", scorer.cleanJsonString(null));
        assertEquals("", scorer.cleanJsonString("   "));
    }

    @Test
    @DisplayName("parseJsonResponse should correctly parse valid JSON with NOW urgency")
    void testParseValidJsonNow() throws Exception {
        String rawLlmContent = "```json\n{\"urgency\": \"NOW\", \"reason\": \"Blocking incident on payment gateway\"}\n```";
        UrgencyResult result = scorer.parseJsonResponse(rawLlmContent);

        assertNotNull(result);
        assertEquals(Urgency.NOW, result.urgency());
        assertEquals("Blocking incident on payment gateway", result.reason());
    }

    @Test
    @DisplayName("parseJsonResponse should correctly parse valid JSON with TODAY urgency")
    void testParseValidJsonToday() throws Exception {
        String rawLlmContent = "{\"urgency\": \"TODAY\", \"reason\": \"Need approval for PR before 5 PM\"}";
        UrgencyResult result = scorer.parseJsonResponse(rawLlmContent);

        assertNotNull(result);
        assertEquals(Urgency.TODAY, result.urgency());
        assertEquals("Need approval for PR before 5 PM", result.reason());
    }

    @Test
    @DisplayName("parseJsonResponse should fallback urgency to THIS_WEEK when urgency field is absent")
    void testParseMissingUrgencyField() throws Exception {
        String rawLlmContent = "{\"reason\": \"General update on roadmap\"}";
        UrgencyResult result = scorer.parseJsonResponse(rawLlmContent);

        assertNotNull(result);
        assertEquals(Urgency.THIS_WEEK, result.urgency());
        assertEquals("General update on roadmap", result.reason());
    }

    @Test
    @DisplayName("score should throw IllegalStateException when API key is blank or null")
    void testScoreThrowsWhenApiKeyIsMissing() {
        GroqUrgencyScorer missingKeyScorer = new GroqUrgencyScorer(RestClient.builder(), "");
        ScoringContext context = new ScoringContext("Prod is down!", "John Doe", "john@example.com", "DIRECT_REPORT", "incident-channel", "", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> missingKeyScorer.score(context));
        assertTrue(ex.getMessage().contains("Groq API Key is missing"));
    }
}
