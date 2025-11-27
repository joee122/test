package com.example.aicodereview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OllamaClient {
    private final WebClient webClient;
    private final String baseUrl;
    private final String model;

    public OllamaClient(WebClient ollamaWebClient,
                        @Value("${ollama.url:http://localhost:11434}") String baseUrl,
                        @Value("${ollama.model:llama2}") String model) {
        this.webClient = ollamaWebClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
    }

    public String generate(String prompt) {
        String url = baseUrl + "/api/generate";
        try {
            Mono<String> mono = webClient.post()
                    .uri(url)
                    .bodyValue(java.util.Map.of("model", model, "prompt", prompt))
                    .retrieve()
                    .bodyToMono(String.class);
            return mono.block();
        } catch (Exception e) {
            return "Ollama request failed: " + e.getMessage();
        }
    }
}