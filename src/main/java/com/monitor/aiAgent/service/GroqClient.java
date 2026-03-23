package com.monitor.aiAgent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Service
public class GroqClient {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String ask(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String,Object> body = new HashMap<>();
            body.put("model", "llama-3.3-70b-versatile");
            body.put("input", prompt);

            HttpEntity<Map<String,Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(apiUrl, HttpMethod.POST, request, String.class);

            // Parse JSON response body
            JsonNode rootNode = objectMapper.readTree(response.getBody());

            // 1. Check for specific Deepmind/Groq 'output' array format
            JsonNode outputArray = rootNode.path("output");
            if (outputArray.isArray()) {
                for (JsonNode outputItem : outputArray) {
                    if ("message".equals(outputItem.path("type").asText())) {
                        JsonNode contentArray = outputItem.path("content");
                        if (contentArray.isArray()) {
                            for (JsonNode contentItem : contentArray) {
                                if ("output_text".equals(contentItem.path("type").asText())) {
                                    return contentItem.path("text").asText();
                                }
                            }
                        }
                    }
                }
            }

            // 2. Check for standard OpenAI (choices[0].message.content) format
            JsonNode choices = rootNode.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode messageNode = choices.get(0).path("message");
                if (messageNode.has("content")) {
                    return messageNode.path("content").asText();
                }
            }

            // Fallback: Dump whole response if format not recognized
            return response.getBody();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error calling AI API: " + e.getMessage();
        }
    }
}