package com.generic.tools.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.junit.jupiter.api.Test;

public class GeminiTest {

    @Test
    void testGeminiConnection() {
        // Replace with your actual key or set GEMINI_API_KEY env variable
        String apiKey = System.getenv("GEMINI_API_KEY"); 
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Please set the GEMINI_API_KEY environment variable");
        }

        System.out.println("Connecting to Google AI...");

        // Try the standard alias first
        ChatLanguageModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                // ❌ OLD: .modelName("gemini-1.5-flash") 
                // ✅ NEW: Use the one found in your list (minus "models/")
                .modelName("gemini-2.0-flash") 
                .logRequestsAndResponses(true)
                .build();

        try {
            String response = model.generate("Hello, are you online?");
            System.out.println("✅ Success! Model replied: " + response);
        } catch (RuntimeException e) {
            System.err.println("[Runtime Exception]❌ Error connecting to Gemini:");
           // e.printStackTrace();
        } catch (Exception e) {
        	System.err.println("[Exception]❌ Error connecting to Gemini:");
        }
    }
}