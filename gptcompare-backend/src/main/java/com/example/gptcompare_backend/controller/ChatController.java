package com.example.gptcompare_backend.controller;

import com.example.gptcompare_backend.dto.ChatRequest;
import com.example.gptcompare_backend.dto.ChatResponse;
import com.example.gptcompare_backend.service.OpenAIService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:4200")
public class ChatController {

    private final OpenAIService openAIService;

    public ChatController(OpenAIService openAIService) {
        this.openAIService = openAIService;
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<ChatResponse>> sendMessage(@RequestBody ChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return Mono.just(
                    ResponseEntity.badRequest().body(
                            new ChatResponse(
                                    "Message vide.",
                                    0L,
                                    null,
                                    null,
                                    null,
                                    false,
                                    null
                            )
                    )
            );
        }

        return openAIService.generateReply(
                request.getMessage(),
                request.getModel(),
                request.getTemperature(),
                request.getMaxOutputTokens()
        ).map(r -> ResponseEntity.ok(
                new ChatResponse(
                        r.reply(),
                        r.latencyMs(),
                        r.inputTokens(),
                        r.outputTokens(),
                        r.totalTokens(),
                        r.truncated(),
                        r.truncateReason()
                )
        ));
    }

    @GetMapping(value = "/ping", produces = "text/plain; charset=UTF-8")
    public Mono<String> ping() {
        return Mono.just("pong");
    }
}
