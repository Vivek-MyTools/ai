package com.generic.tools.ai.components;

import com.generic.tools.ai.SuperAgentFactory;
import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.ChatMessageData;
import com.generic.tools.ai.helper.UIBackgroundTaskExecutor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessageWindow {
    private String model;
    private String knowledgeBase;
    private ChatMessagesPanel chatMessagesPanel;
    
    private final List<Object> externalTools = new ArrayList<>(); 
    private String customSystemRules;
    
    private Assistant chatAgent;     
    
    // Cache for switching between models without losing their specific state/context
    private Map<String, Assistant> cachedModels = new HashMap<>();

    public ChatMessageWindow(String model, ChatMessagesPanel chatMessagesPanel) {
        this(model, "", chatMessagesPanel, null, "");
    }

    public ChatMessageWindow(String model, String knowledgeBase, ChatMessagesPanel chatMessagesPanel, List<Object> additionalTools, String extraRules) {
        this.model = model;
        this.knowledgeBase = knowledgeBase != null ? knowledgeBase : "";
        this.chatMessagesPanel = chatMessagesPanel;
        this.customSystemRules = extraRules != null ? extraRules : "";
        if(additionalTools != null) {
            externalTools.addAll(additionalTools);
        }
        
        // Initialize the agent immediately
        newAgent();
    }
    
    public void newAgent() {        
        // 1. Capture existing history from the UI (Must be done on current thread/EDT)
        // This ensures that if we loaded a file or switched chats, the new AI knows the context.
        List<ChatMessageData> currentHistory = new ArrayList<>();
        if (chatMessagesPanel != null) {
            currentHistory.addAll(chatMessagesPanel.getMessageHistory());
        }

        UIBackgroundTaskExecutor.run(() -> {
            // Check cache first? 
            // Usually, if we are creating a "newAgent", we want to ensure it has the latest context.
            // If the model is already in cache, it likely has the memory, 
            // BUT if we just loaded from a file, the cache is empty.
            
            if (cachedModels.containsKey(model)) {
                chatAgent = cachedModels.get(model);
                // Optional: If you support injecting history into an existing agent, do it here.
            } else {
                Assistant agent = createAgent(currentHistory);
                chatAgent = agent;
                cachedModels.put(model, agent);
            }
        });
    }    
    
    private Assistant createAgent(List<ChatMessageData> history) {
        SuperAgentFactory factory = new SuperAgentFactory(model);

        String fullSystemPrompt = SuperAgentFactory.DEFAULT_SYSTEM_PROMPT;
        if (!customSystemRules.isEmpty()) {
            fullSystemPrompt += "\n" + customSystemRules;
        }

        // --- CONTEXT INJECTION ---
        // You need to update your SuperAgentFactory.createSuperAgent signature 
        // to accept 'history' and populate the ChatMemory.
        return factory.createSuperAgent(
                knowledgeBase,
                fullSystemPrompt,
                externalTools.toArray(),
                history // <--- Pass history here
        );
    }
    
    // Getters & Setters
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public ChatMessagesPanel getChatMessagesPanel() {
        return chatMessagesPanel;
    }

    public void setChatMessagesPanel(ChatMessagesPanel chatMessagesPanel) {
        this.chatMessagesPanel = chatMessagesPanel;
    }    

    public Assistant getChatAgent() {
        if (cachedModels.containsKey(model)) {
            chatAgent = cachedModels.get(model);
            return chatAgent;
        }
        
        // Fallback if accessed before async init completes
        // (Though ideally, newAgent() handles the population)
        List<ChatMessageData> history = (chatMessagesPanel != null) 
                ? chatMessagesPanel.getMessageHistory() 
                : new ArrayList<>();
                
        Assistant agent = createAgent(history);
        cachedModels.put(model, agent);
        chatAgent = agent;
        return chatAgent;
    }

    public void setChatAgent(Assistant chatAgent) {
        this.chatAgent = chatAgent;
        if (model != null && chatAgent != null) {
            cachedModels.put(model, chatAgent);
        }
    }
    
}