package com.generic.tools.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class CalculatorTool {

	@Tool("""
	        Performs mathematical calculations.
	        
	        STRICT USAGE RULES:
	        1. Input MUST be a pure mathematical equation containing numbers and operators (e.g., "55 * 4", "12.5 + 10", "(50/2)^2").
	        2. Input MUST NOT contain text, sentences, or definitions.
	        3. If the user asks "What is X?" and X is a concept (like "Dependency Injection"), DO NOT USE THIS TOOL.
	        4. If the user asks "What is X?" and X is a math problem (like "5 + 5"), USE THIS TOOL.
	        """)
    public double calculate(String expression) {
        System.out.println("   --> 🧮 Calculator Tool: " + expression);
        try {
            // exp4j safely evaluates the string equation
            Expression e = new ExpressionBuilder(expression).build();
            return e.evaluate();
        } catch (Exception e) {
            System.err.println("Math error: " + e.getMessage());
            return 0; // Or throw exception
        }
    }
}