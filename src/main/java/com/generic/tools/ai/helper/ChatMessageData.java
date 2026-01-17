package com.generic.tools.ai.helper;

import java.io.Serializable;

public class ChatMessageData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String role; // "USER" or "AGENT"
    public String content;

    public ChatMessageData(String role, String content) {
        this.role = role;
        this.content = content;
    }
}