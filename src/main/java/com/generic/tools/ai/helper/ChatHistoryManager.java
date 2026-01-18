package com.generic.tools.ai.helper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ChatHistoryManager {

    private static final String FILE_PATH = System.getProperty("user.home") + "/.chats/chat_history.dat";

    public static void saveChats(List<ChatSession> sessions) {
        try {
            // Ensure directory exists
            Files.createDirectories(Paths.get(FILE_PATH).getParent());
            
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_PATH))) {
                oos.writeObject(sessions);
            }
        } catch (IOException e) {
            e.printStackTrace(); // Log error appropriately
        }
    }

     public static List<ChatSession> loadChats() {
        java.nio.file.Path path = java.nio.file.Paths.get(FILE_PATH);
        if (!java.nio.file.Files.exists(path)) {
            return new ArrayList<>();
        }

        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.FileInputStream(FILE_PATH))) {
            return (List<ChatSession>) ois.readObject();
        } catch (Exception e) {
            // This catches StreamCorruptedException, InvalidClassException, ClassNotFoundException, etc.
            System.err.println("⚠️ Warning: Chat history is incompatible or corrupted. Resetting history.");
            System.err.println("   -> Reason: " + e.getMessage());

            // Optional: Delete the bad file immediately so it doesn't annoy you next time
            try {
                java.nio.file.Files.deleteIfExists(path);
            } catch (java.io.IOException ignored) {
            }

            return new ArrayList<>();
        }
    }
}