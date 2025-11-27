package com.example.aicodereview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class OllamaClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;

    public OllamaClient(RestTemplate restTemplate,
                        @Value("${ollama.url:http://localhost:11434}") String baseUrl,
                        @Value("${ollama.model:llama2}") String model) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
    }

    /**
     * Sends the prompt to Ollama's /api/generate endpoint and returns the raw response body as text.
     * NOTE: If your Ollama is configured to stream, you may need a streaming HTTP client. This simple
     * implementation expects a synchronous JSON/text response.
     */
    public String generate(String prompt) {
        String url = baseUrl + "/api/generate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt
        );
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(url, req, String.class);
        if (resp.getStatusCode().is2xxSuccessful()) {
            return resp.getBody() == null ? "" : resp.getBody();
        }
        return "Ollama request failed: " + resp.getStatusCode().toString();
    }
}