package com.generic.tools.ai;

import com.generic.tools.ai.agent.Assistant;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) {
    	
    	String documentPath = "src/main/resources/my_docs"; 
    	
    	SuperAgentFactory factory = new SuperAgentFactory("http://localhost:11434", "llama3.2");
    	Assistant agent =  factory.createSuperAgent(documentPath);
    	ChatRunner.run(agent);
    }
}
