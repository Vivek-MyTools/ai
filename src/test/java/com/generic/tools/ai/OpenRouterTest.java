package com.generic.tools.ai;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.OpenRouterHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OpenRouterTest {

    @Test
    void testOpenRouterHelperModelResolution() {
        assertEquals("meta-llama/llama-3.3-70b-instruct", OpenRouterHelper.resolveModelName("gpt-oss:20b-cloud"));
        assertEquals("qwen/qwen-2.5-coder-32b-instruct:free", OpenRouterHelper.resolveModelName("qwen3-coder:480b-cloud"));
        assertEquals("meta-llama/llama-3.3-70b-instruct", OpenRouterHelper.resolveModelName("meta-llama/llama-3.3-70b-instruct:free"));
    }

    @Test
    void testIsOpenRouterModel() {
        assertTrue(OpenRouterHelper.isOpenRouterModel("meta-llama/llama-3.3-70b-instruct"));
        assertTrue(OpenRouterHelper.isOpenRouterModel("google/gemini-2.0-flash-exp:free"));
        assertTrue(OpenRouterHelper.isOpenRouterModel("deepseek/deepseek-r1:free"));
        assertTrue(OpenRouterHelper.isOpenRouterModel("gpt-oss:20b-cloud"));
        assertFalse(OpenRouterHelper.isOpenRouterModel("tinyllama"));
    }

    @Test
    void testOpenRouterConnectionIfKeyPresent() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("⚠️ OPENROUTER_API_KEY is not set. Skipping live test.");
            return;
        }

        System.out.println("Testing live OpenRouter connection...");
        SuperAgentFactory factory = new SuperAgentFactory("google/gemini-2.0-flash-exp:free");
        Assistant assistant = factory.createSuperAgent();
        String response = assistant.chat("Say 'Hello OpenRouter' and nothing else.");
        System.out.println("✅ OpenRouter Response: " + response);
        assertNotNull(response);
    }
}
