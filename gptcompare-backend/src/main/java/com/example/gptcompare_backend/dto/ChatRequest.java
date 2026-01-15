package com.example.gptcompare_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ChatRequest {

    // message utilisateur
    private String message;

    // paramètres
    private String model;              // ex: "gpt-5-mini"
    private Double temperature;        // ex: 0.7
    private Integer maxOutputTokens;   // ex: 800

    public ChatRequest() {}

}
