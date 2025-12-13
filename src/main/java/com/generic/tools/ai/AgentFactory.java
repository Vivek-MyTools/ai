package com.generic.tools.ai;

import java.time.Duration;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.tools.CalculatorTool;
import com.generic.tools.ai.tools.StockTool;
import com.generic.tools.ai.tools.WeatherTool;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;

public class AgentFactory {

    private final String baseUrl;
    private final String modelName;

    // Default Constructor (Uses default settings)
    public AgentFactory() {
        this.baseUrl = "http://localhost:11434";
        this.modelName = "gpt-oss:20b-cloud";
    }

    // Flexible Constructor (Allows custom settings)
    public AgentFactory(String baseUrl, String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    public Assistant createAssistant() {
        // 1. Configure Model using instance variables
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(this.baseUrl)
                .modelName(this.modelName)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(60))
                .build();

        // 2. Build and Return Agent
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(new WeatherTool(), new StockTool(), new CalculatorTool()) 
                .build();
    }
}