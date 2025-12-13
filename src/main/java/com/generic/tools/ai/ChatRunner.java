package com.generic.tools.ai;

import com.generic.tools.ai.agent.Assistant;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class ChatRunner {

    private static final String LOG_FILE = "conversation_history.log";

    public static void run(Assistant agent) {
        System.out.println("✅ Agent Ready! (Type 'exit' to quit)");
        System.out.println("📝 Logging conversation to: " + LOG_FILE);
        System.out.print("\nUser: ");

        // Try-with-resources handles closing both Scanner and File Writers automatically
        try (Scanner scanner = new Scanner(System.in);
             FileWriter fw = new FileWriter(LOG_FILE, true); // 'true' enables append mode
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter logger = new PrintWriter(bw)) {

            // Log the start of a new session with a timestamp
            log(logger, "\n--- Session Started: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " ---");

            while (scanner.hasNextLine()) {
                String text = scanner.nextLine();

                if ("exit".equalsIgnoreCase(text)) {
                    System.out.println("Goodbye! 👋");
                    log(logger, "[Session Ended by User]");
                    break;
                }

                // 1. Log User Input
                log(logger, "User: " + text);

                try {
                    String response = agent.chat(text);
                    System.out.println("Agent: " + response);
                    
                    // 2. Log Agent Response
                    log(logger, "Agent: " + response);
                    
                } catch (Exception e) {
                    String errorMsg = "❌ Error: " + e.getMessage();
                    System.err.println(errorMsg);
                    log(logger, errorMsg);
                }

                System.out.print("\nUser: ");
            }
        } catch (IOException e) {
            System.err.println("⚠️ Warning: Could not access log file. Chats will not be saved. (" + e.getMessage() + ")");
        }
    }

    /**
     * Helper to write to file and force save (flush) immediately.
     */
    private static void log(PrintWriter writer, String message) {
        writer.println(message);
        writer.flush(); // Ensures data is saved even if the program crashes later
    }
}