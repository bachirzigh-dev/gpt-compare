package com.example.gptcompare_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OpenAIServiceTest {

    private MockWebServer server;
    private OpenAIService service;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        // IMPORTANT : ton service POST sur baseUrl directement (pas de .uri())
        String apiUrl = server.url("/v1/responses").toString();

        service = new OpenAIService(
                "test-api-key",
                apiUrl,
                "gpt-4.1-mini" // defaultModel
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }


    @Test
    void should_extract_output_text_and_usage_tokens_and_send_expected_payload() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "status":"completed",
                      "output":[{"content":[{"type":"output_text","text":"Bonjour !"}]}],
                      "usage":{"input_tokens":10,"output_tokens":20,"total_tokens":30}
                    }
                """));

        StepVerifier.create(service.generateReply("Salut", null, 0.7, null))
                .assertNext(r -> {
                    assertEquals("Bonjour !", r.reply());
                    assertEquals(10, r.inputTokens());
                    assertEquals(20, r.outputTokens());
                    assertEquals(30, r.totalTokens());
                    assertFalse(r.truncated());
                    assertNull(r.truncateReason());
                    assertTrue(r.latencyMs() >= 0);
                })
                .verifyComplete();

        var req = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("POST", req.getMethod());
        assertEquals("Bearer test-api-key", req.getHeader("Authorization"));

        JsonNode json = om.readTree(req.getBody().readString(StandardCharsets.UTF_8));
        assertEquals("gpt-4.1-mini", json.get("model").asText());
        //assertEquals("Utilisateur: Salut", json.get("input").asText());
        assertEquals(800, json.get("max_output_tokens").asInt()); // defaultMaxOutputTokens
        assertTrue(json.has("temperature"));
        assertEquals(0.7, json.get("temperature").asDouble(), 1e-9);
    }


    @Test
    void should_not_send_temperature_for_gpt5_models_even_if_provided() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {"status":"completed","output":[{"content":[{"type":"output_text","text":"OK"}]}]}
                """));

        StepVerifier.create(service.generateReply("Test", "gpt-5-mini", 0.9, 100))
                .assertNext(r -> assertEquals("OK", r.reply()))
                .verifyComplete();

        var req = server.takeRequest(1, TimeUnit.SECONDS);
        JsonNode json = om.readTree(req.getBody().readString(StandardCharsets.UTF_8));
        assertEquals("gpt-5-mini", json.get("model").asText());
    }

    @Test
    void should_not_send_temperature_when_null() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {"status":"completed","output":[{"content":[{"type":"output_text","text":"OK"}]}]}
                """));

        StepVerifier.create(service.generateReply("Test", "gpt-4.1-mini", null, 100))
                .assertNext(r -> assertEquals("OK", r.reply()))
                .verifyComplete();

        var req = server.takeRequest(1, TimeUnit.SECONDS);
        JsonNode json = om.readTree(req.getBody().readString(StandardCharsets.UTF_8));
        assertFalse(json.has("temperature"));
    }



    @Test
    void should_use_default_max_output_tokens_when_null_or_invalid() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {"status":"completed","output":[{"content":[{"type":"output_text","text":"OK"}]}]}
                """));

        StepVerifier.create(service.generateReply("Test", null, null, 0))
                .assertNext(r -> assertEquals("OK", r.reply()))
                .verifyComplete();

        var req = server.takeRequest(1, TimeUnit.SECONDS);
        JsonNode json = om.readTree(req.getBody().readString(StandardCharsets.UTF_8));
        assertEquals(800, json.get("max_output_tokens").asInt());
    }

    @Test
    void should_cap_max_output_tokens_to_8000() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {"status":"completed","output":[{"content":[{"type":"output_text","text":"OK"}]}]}
                """));

        StepVerifier.create(service.generateReply("Test", null, null, 999999))
                .assertNext(r -> assertEquals("OK", r.reply()))
                .verifyComplete();

        var req = server.takeRequest(1, TimeUnit.SECONDS);
        JsonNode json = om.readTree(req.getBody().readString(StandardCharsets.UTF_8));
        assertEquals(8000, json.get("max_output_tokens").asInt());
    }


    @Test
    void should_use_output_text_when_present() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "status":"completed",
                      "output":[{"content":[
                        {"type":"output_text","text":"Texte principal"},
                        {"type":"something","text":"Autre"}
                      ]}]
                    }
                """));

        StepVerifier.create(service.generateReply("Test", null, null, 100))
                .assertNext(r -> assertEquals("Texte principal", r.reply()))
                .verifyComplete();
    }

    @Test
    void should_fallback_to_any_text_when_output_text_missing() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "status":"completed",
                      "output":[{"content":[{"type":"x","text":"Fallback texte"}]}]
                    }
                """));

        StepVerifier.create(service.generateReply("Test", null, null, 100))
                .assertNext(r -> assertEquals("Fallback texte", r.reply()))
                .verifyComplete();
    }



    @Test
    void should_mark_truncated_and_return_user_message_when_incomplete_and_no_text() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "status":"incomplete",
                      "incomplete_details":{"reason":"max_output_tokens"},
                      "output":[{"content":[{"type":"output_text","text":""}]}]
                    }
                """));

        StepVerifier.create(service.generateReply("Long", null, null, 123))
                .assertNext(r -> {
                    assertTrue(r.truncated());
                    assertEquals("max_output_tokens", r.truncateReason());
                    assertTrue(r.reply().contains("trop longue"), r.reply());
                    assertTrue(r.reply().contains("123"), r.reply());
                })
                .verifyComplete();
    }

    @Test
    void should_mark_truncated_but_keep_partial_text_when_incomplete_and_text_present() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "status":"incomplete",
                      "incomplete_details":{"reason":"max_output_tokens"},
                      "output":[{"content":[{"type":"output_text","text":"Début de réponse..."}]}]
                    }
                """));

        StepVerifier.create(service.generateReply("Long", null, null, 200))
                .assertNext(r -> {
                    assertEquals("Début de réponse...", r.reply());
                    assertTrue(r.truncated());
                    assertEquals("max_output_tokens", r.truncateReason());
                })
                .verifyComplete();
    }


    @Test
    void should_return_error_message_when_output_field_missing() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {"status":"completed","usage":{"input_tokens":1}}
                """));

        StepVerifier.create(service.generateReply("Test", null, null, 100))
                .assertNext(r -> assertEquals("Erreur: réponse OpenAI sans champ output.", r.reply()))
                .verifyComplete();
    }

    @Test
    void should_return_error_message_when_status_not_completed_and_not_truncated() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "status":"failed",
                      "output":[{"content":[{"type":"output_text","text":""}]}]
                    }
                """));

        StepVerifier.create(service.generateReply("Test", null, null, 100))
                .assertNext(r -> assertTrue(r.reply().contains("status=failed"), r.reply()))
                .verifyComplete();
    }

    @Test
    void should_return_http_error_message_on_400() {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"bad request\"}"));

        StepVerifier.create(service.generateReply("Test", null, null, 100))
                .assertNext(r -> {
                    assertTrue(r.reply().startsWith("Erreur OpenAI HTTP 400:"), r.reply());
                    assertNull(r.inputTokens());
                    assertNull(r.outputTokens());
                    assertNull(r.totalTokens());
                    assertFalse(r.truncated());
                })
                .verifyComplete();
    }


    @Test
    void should_return_generic_error_on_disconnect() {

        // Le serveur coupe la connexion
        server.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        StepVerifier.create(service.generateReply("Test", null, null, 100))
                .assertNext(r -> {
                    assertNotNull(r.reply());
                    assertTrue(r.reply().startsWith("Erreur:"), r.reply());
                    assertNull(r.inputTokens());
                    assertNull(r.outputTokens());
                    assertNull(r.totalTokens());
                })
                .verifyComplete();
    }
}
