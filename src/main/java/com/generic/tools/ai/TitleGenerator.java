package com.generic.tools.ai;

import com.generic.tools.ai.agent.Assistant;

public class TitleGenerator {
    
    private final Assistant assistant;

    // 1. TEMPLATE: Make this static final so it doesn't get overwritten.
    // We use "System" instructions to force brevity.
    private static final String PROMPT_TEMPLATE = """
            You are a system that generates conversation titles.
            
            Rules:
            1. Summarize the "User Input" into a concise title (3-6 words).
            2. Do NOT use quotes, markdown, or punctuation.
            3. Do NOT say "Here is a title". Just output the title text.
            4. If the input is code, title it based on the language and function (e.g., "Java Binary Search").
            
            User Input:
            %s
            
            Title:
            """;
    
    public TitleGenerator(String model) {        
        SuperAgentFactory factory = new SuperAgentFactory(model);        
        this.assistant = factory.createSuperAgent();
    }
    
    public String generateTitle(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "New Chat";
        }

        // 2. TRUNCATE: Only look at the first 300 characters. 
        // The topic is usually in the first sentence. 
        // This saves tokens and prevents errors with massive copy-pastes.
        String context = userMessage.length() > 300 
                ? userMessage.substring(0, 300) + "..." 
                : userMessage;

        // 3. FORMAT: Create a new string every time. Never modify the constant template.
        String finalPrompt = String.format(PROMPT_TEMPLATE, context);

        try {
            String response = assistant.chat(finalPrompt);        
            return sanitizeResponse(response);
        } catch (Exception e) {
            // Fallback if AI fails
            return "New Chat";
        }
    }

    // 4. CLEANUP: Ensure the result is clean for a UI label
    private String sanitizeResponse(String response) {
        if (response == null) return "New Chat";

        String clean = response.trim();
        
        // Remove surrounding quotes if the AI added them
        clean = clean.replaceAll("^\"|\"$", "");
        
        // Remove common prefixes if AI ignored instructions
        clean = clean.replaceAll("^(Title:|Subject:|Here is the title:)\\s*", "");
        
        // Final sanity check on length
        if (clean.length() > 50) {
            return clean.substring(0, 47) + "...";
        }
        
        return clean;
    }
}