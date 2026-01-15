package com.example.gptcompare_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ChatResponse {

    private String reply;
    private Long latencyMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;

    private Boolean truncated; // true si coupé par max_output_tokens
    private String truncateReason; // ex: "max_output_tokens"

    @SuppressWarnings("unused")
    public ChatResponse() {}

    public ChatResponse(String reply, Long latencyMs, Integer inputTokens, Integer outputTokens, Integer totalTokens,
                        Boolean truncated, String truncateReason) {
        this.reply = reply;
        this.latencyMs = latencyMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.truncated = truncated;
        this.truncateReason = truncateReason;
    }

}
