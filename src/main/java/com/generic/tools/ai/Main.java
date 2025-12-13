package com.generic.tools.ai;

import com.generic.tools.ai.agent.Assistant;

public class Main {

    public static void main(String[] args) {
        System.out.println("--- 🤖 Initializing RAG AI Agent ---");

        String documentPath = "src/main/resources/my_docs"; 

        // 1. Create the Factory (Pass custom model if needed)
        // Example: new RagAgentFactory("http://localhost:11434", "llama3.2");
        SuperAgentFactory factory = new SuperAgentFactory(); 
        
        // 2. Create the Agent using the instance method
        Assistant agent = factory.createSuperAgent(documentPath);
        
        // 3. Run it
        ChatRunner.run(agent);
    }
}