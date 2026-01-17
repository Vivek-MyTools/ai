package com.generic.tools.ai.helper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ChatHistoryManager {

    private static final String FILE_PATH = System.getProperty("user.home") + "/.generic_ai_tools/chat_history.dat";

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

    @SuppressWarnings("unchecked")
    public static List<ChatSession> loadChats() {
        if (!Files.exists(Paths.get(FILE_PATH))) {
            return new ArrayList<>();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE_PATH))) {
            return (List<ChatSession>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}