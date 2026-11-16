package com.generic.tools.ai;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.OpenRouterHelper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class OpenRouterTest {

    @Test
    void testOpenRouterHelperModelResolution() {
        assertEquals("meta-llama/llama-3.3-70b-instruct", OpenRouterHelper.resolveModelName("gpt-oss:20b-cloud"));
        assertEquals("qwen/qwen-2.5-coder-32b-instruct", OpenRouterHelper.resolveModelName("qwen3-coder:480b-cloud"));
        assertEquals("meta-llama/llama-3.3-70b-instruct", OpenRouterHelper.resolveModelName("meta-llama/llama-3.3-70b-instruct:free"));
        assertEquals("meta-llama/llama-3.3-70b-instruct", OpenRouterHelper.resolveModelName("meta-llama/llama-3.3-70b-instruct"));
    }

    @Test
    void testIsOpenRouterModel() {
        assertTrue(OpenRouterHelper.isOpenRouterModel("meta-llama/llama-3.3-70b-instruct"));
        assertTrue(OpenRouterHelper.isOpenRouterModel("google/gemini-2.0-flash-exp"));
        assertTrue(OpenRouterHelper.isOpenRouterModel("deepseek/deepseek-r1"));
        assertTrue(OpenRouterHelper.isOpenRouterModel("gpt-oss:20b-cloud"));
        assertFalse(OpenRouterHelper.isOpenRouterModel("tinyllama"));
    }

    @Test
    void testOpenRouterToolCallingSupport() {
        String apiKey = OpenRouterHelper.getApiKey(null);
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("⚠️ OpenRouter API key not found in env or registry. Skipping tool test.");
            return;
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            String jsonPayload = """
                {
                    "model": "meta-llama/llama-3.3-70b-instruct",
                    "messages": [{"role": "user", "content": "What is 2 + 2?"}],
                    "tools": [{
                        "type": "function",
                        "function": {
                            "name": "calculator",
                            "description": "Calculate math",
                            "parameters": {
                                "type": "object",
                                "properties": {
                                    "expression": {"type": "string"}
                                },
                                "required": ["expression"]
                            }
                        }
                    }]
                }
                """;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://github.com/generic-tools/ai")
                    .header("X-Title", "AI Assistant")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("📡 OpenRouter Tool Call Test HTTP Status: " + response.statusCode());
            System.out.println("📡 OpenRouter Tool Call Response:\n" + response.body());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
