package com.generic.tools.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class CalculatorTool {

    @Tool("Calculates the result of a mathematical expression (e.g., '2 + 2', '4.5 * 12').")
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