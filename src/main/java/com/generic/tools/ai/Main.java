package com.generic.tools.ai;

import javax.swing.SwingUtilities;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.tools.CalculatorTool;
import com.generic.tools.ai.tools.WeatherTool;

public class Main {

	public static void main(String[] args) {
		System.out.println("--- 🤖 Initializing RAG AI Agent ---");

		String documentPath = "src/main/resources/my_docs";

		WeatherTool weatherTool = new WeatherTool();
		CalculatorTool calcTool = new CalculatorTool();

		// 1. Create the Factory (Pass custom model if needed)
		// Example: new RagAgentFactory("http://localhost:11434", "llama3.2");
		SuperAgentFactory factory = new SuperAgentFactory("http://localhost:11434", "llama3.2");

		// 2. Create the Agent using the instance method
		Assistant agent = factory.createSuperAgent(documentPath, calcTool, weatherTool);

		// 3. Run it
		ChatRunner.run(agent);
		
		
		
	}
}