package com.generic.tools.ai.helper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// Represents a single chat session (e.g., "Java Help (2)")
public class ChatSession implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String name;
    public String modelName;
    public List<ChatMessageData> messages = new ArrayList<>();

    public ChatSession(String name, String modelName) {
        this.name = name;
        this.modelName = modelName;
    }
}
