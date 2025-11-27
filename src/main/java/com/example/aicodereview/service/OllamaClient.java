package com.example.aicodereview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.function.Consumer;

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
            return webClient.post()
                    .uri(url)
                    .bodyValue(java.util.Map.of("model", model, "prompt", prompt))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(120));
        } catch (Exception e) {
            return "Ollama request failed: " + e.getMessage();
        }
    }

    /**
     * Stream generate: invokes Ollama and streams text chunks to the provided chunkHandler.
     * This method blocks until stream completes or error occurs.
     */
    public void streamGenerate(String prompt, Consumer<String> chunkHandler) {
        String url = baseUrl + "/api/generate";
        try {
            Flux<String> flux = webClient.post()
                    .uri(url)
                    .bodyValue(java.util.Map.of("model", model, "prompt", prompt))
                    .retrieve()
                    .bodyToFlux(String.class);

            flux.doOnNext(chunkHandler::accept)
                .doOnError(err -> chunkHandler.accept("[Ollama stream error: " + err.getMessage() + "]"))
                .blockLast(Duration.ofSeconds(300));
        } catch (Exception e) {
            chunkHandler.accept("Ollama request failed: " + e.getMessage());
        }
    }
}