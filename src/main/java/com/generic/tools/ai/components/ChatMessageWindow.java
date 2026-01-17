package com.generic.tools.ai.components;

import com.generic.tools.ai.SuperAgentFactory;
import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.UIBackgroundTaskExecutor;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageWindow {
    private String model;
    private String knowledgeBase;
    private ChatMessagesPanel chatMessagesPanel;
    
    private final List<Object> externalTools = new ArrayList<>(); 
    private String customSystemRules;
    
    private Assistant chatAgent;    
   

    public ChatMessageWindow(String model, ChatMessagesPanel chatMessagesPanel) {
        this(model, "", chatMessagesPanel, null, "");
    }

    public ChatMessageWindow(String model, String knowledgeBase, ChatMessagesPanel chatMessagesPanel, List<Object> additionalTools, String extraRules) {
        this.model = model;
        this.knowledgeBase = knowledgeBase != null ? knowledgeBase : "";
        this.chatMessagesPanel = chatMessagesPanel;
        this.customSystemRules = extraRules != null ? extraRules : "";
        if(additionalTools != null) {
            externalTools.addAll(externalTools);
        }
        
        newAgent();
    }
    
    public void newAgent() {
        UIBackgroundTaskExecutor.run(() -> {
            SuperAgentFactory factory = new SuperAgentFactory("http://localhost:11434", model);

            String fullSystemPrompt = SuperAgentFactory.DEFAULT_SYSTEM_PROMPT;
            if (!customSystemRules.isEmpty()) {
                fullSystemPrompt += "\n" + customSystemRules;
            }

            chatAgent = factory.createSuperAgent(
                    knowledgeBase,
                    fullSystemPrompt,
                    externalTools.toArray()
            );
        });
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
        return chatAgent;
    }

    public void setChatAgent(Assistant chatAgent) {
        this.chatAgent = chatAgent;
    }
    
}
