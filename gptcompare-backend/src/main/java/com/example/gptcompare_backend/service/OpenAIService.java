package com.example.gptcompare_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    /**
     * Résultat normalisé renvoyé par le service OpenAI, prêt à être exposé au controller.
     * - reply : texte final (ou message d'erreur user-friendly)
     * - latencyMs : durée mesurée côté backend
     * - tokens : usage si disponible
     * - truncated : true si OpenAI a stoppé à cause de max_output_tokens
     */
    public record AiResult(
            String reply,
            long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            boolean truncated,
            String truncateReason
    ) {}

    private final WebClient webClient;
    private final String defaultModel;

    /** Valeur par défaut si maxOutputTokens est absent ou invalide. */
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 800;
    /** Garde-fou pour éviter des sorties déraisonnables côté serveur. */
    private static final int HARD_MAX_OUTPUT_TOKENS = 8000;

    public OpenAIService(
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.api.url}") String apiUrl,
            @Value("${openai.api.model}") String defaultModel
    ) {
        this.defaultModel = defaultModel;
        this.webClient = WebClient.builder()
                // Ici apiUrl pointe directement sur /v1/responses
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<AiResult> generateReply(String userMessage, String model, Double temperature, Integer maxOutputTokens) {
        final String usedModel = (model == null || model.isBlank()) ? defaultModel : model;

        final int usedMax = Math.min(
                (maxOutputTokens == null || maxOutputTokens < 1)
                        ? DEFAULT_MAX_OUTPUT_TOKENS
                        : maxOutputTokens,
                HARD_MAX_OUTPUT_TOKENS
        );

        // Payload attendu par l’API Responses
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", usedModel);
        payload.put("input", userMessage);
        payload.put("max_output_tokens", usedMax);

        // Température : certains modèles (ex: GPT-5) ne la supportent pas → on l'omet (plutôt que null)
        if (temperature != null && supportsTemperature(usedModel)) {
            payload.put("temperature", temperature);
        }

        final long startMs = System.currentTimeMillis();

        return webClient.post()
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(60))
                .map(res -> toAiResult(res, usedMax, startMs))
                .onErrorResume(WebClientResponseException.class, e -> Mono.just(httpErrorResult(e, startMs)))
                .onErrorResume(e -> Mono.just(genericErrorResult(e, startMs)));
    }

    private static boolean supportsTemperature(String model) {
        return model != null && !model.toLowerCase().startsWith("gpt-5");
    }

    private static AiResult httpErrorResult(WebClientResponseException e, long startMs) {
        long latency = System.currentTimeMillis() - startMs;
        return new AiResult(
                "Erreur OpenAI HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                latency, null, null, null,
                false, null
        );
    }

    private static AiResult genericErrorResult(Throwable e, long startMs) {
        long latency = System.currentTimeMillis() - startMs;
        return new AiResult(
                "Erreur: " + e.getMessage(),
                latency, null, null, null,
                false, null
        );
    }

    private static AiResult toAiResult(Map<?, ?> res, int usedMaxTokens, long startMs) {
        long latency = System.currentTimeMillis() - startMs;

        boolean truncated = isTruncated(res);
        String reason = truncated ? "max_output_tokens" : null;

        String reply = extractTextFromMap(res, usedMaxTokens);

        Integer inTok = null, outTok = null, totalTok = null;
        if (res != null) {
            Object usageObj = res.get("usage");
            if (usageObj instanceof Map<?, ?> usage) {
                inTok = toInt(usage.get("input_tokens"));
                outTok = toInt(usage.get("output_tokens"));
                totalTok = toInt(usage.get("total_tokens"));
            }
        }

        return new AiResult(reply, latency, inTok, outTok, totalTok, truncated, reason);
    }

    private static boolean isTruncated(Map<?, ?> res) {
        if (res == null) return false;
        Object status = res.get("status");
        Object incompleteDetails = res.get("incomplete_details");
        return "incomplete".equals(String.valueOf(status))
                && incompleteDetails instanceof Map<?, ?> details
                && "max_output_tokens".equals(String.valueOf(details.get("reason")));
    }

    private static Integer toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return null;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Extraction du texte :
     * 1) On cherche d’abord type=output_text (forme standard).
     * 2) Sinon on prend le premier champ text non vide.
     * 3) Si truncated et aucun texte → message explicite.
     */
    private static String extractTextFromMap(Map<?, ?> res, int usedMaxTokens) {
        if (res == null) return "Erreur: réponse OpenAI nulle.";

        Object outputObj = res.get("output");
        if (!(outputObj instanceof List<?> output)) {
            return "Erreur: réponse OpenAI sans champ output.";
        }

        // 1) output_text prioritaire
        for (Object itemObj : output) {
            if (!(itemObj instanceof Map<?, ?> item)) continue;
            Object contentObj = item.get("content");
            if (!(contentObj instanceof List<?> content)) continue;

            for (Object cObj : content) {
                if (!(cObj instanceof Map<?, ?> c)) continue;
                Object typeObj = c.get("type");
                Object textObj = c.get("text");
                if ("output_text".equals(typeObj) && textObj instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }

        // 2) fallback sur n'importe quel texte non vide
        for (Object itemObj : output) {
            if (!(itemObj instanceof Map<?, ?> item)) continue;
            Object contentObj = item.get("content");
            if (!(contentObj instanceof List<?> content)) continue;

            for (Object cObj : content) {
                if (!(cObj instanceof Map<?, ?> c)) continue;
                Object textObj = c.get("text");
                if (textObj instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }

        // 3) si on sait que c'est tronqué mais pas de texte exploitable
        if (isTruncated(res)) {
            return "La réponse est trop longue et a dépassé la limite de tokens de sortie (" + usedMaxTokens + "). "
                    + "Augmente maxOutputTokens ou demande une réponse plus courte.";
        }

        Object status = res.get("status");
        if (status != null && !"completed".equals(String.valueOf(status))) {
            return "Erreur: réponse OpenAI non complétée (status=" + status + ").";
        }

        return "Erreur: aucune réponse générée par OpenAI.";
    }
}

