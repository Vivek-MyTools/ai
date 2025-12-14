package com.generic.tools.ai;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.tools.CalculatorTool;
import com.generic.tools.ai.tools.WeatherTool;

public class DesktopChatApp extends JFrame {

    private static final long serialVersionUID = -6839188219549732510L;
    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JComboBox<String> modelSelector;
    
    // Cache to store one Assistant instance per model name
    private final Map<String, Assistant> agentCache = new HashMap<>();

    // Current active agent
    private Assistant agent; 

    public DesktopChatApp() {
        super("AI Super Agent (Local)");
        setSize(600, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- 1. TOP PANEL (Model Selection) ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        JLabel modelLabel = new JLabel("Select Model:");
        
        String[] models = {
                "gpt-oss:20b-cloud",
                "llama3.2", 
                "mistral",                
                "tinyllama"
        };
        modelSelector = new JComboBox<>(models);
        
        modelSelector.addActionListener(e -> {
            String selectedModel = (String) modelSelector.getSelectedItem();
            reloadAgent(selectedModel);
        });

        topPanel.add(modelLabel);
        topPanel.add(modelSelector);
        add(topPanel, BorderLayout.NORTH);

        // --- 2. CENTER PANEL (Chat Area) ---
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        add(scrollPane, BorderLayout.CENTER);

        // --- 3. BOTTOM PANEL (Input) ---
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        // --- 4. INITIALIZATION ---
        sendButton.addActionListener(this::sendMessage);
        inputField.addActionListener(this::sendMessage);

        // Initialize with default
        reloadAgent(models[0]);
    }

    private void reloadAgent(String modelName) {
        // Disable UI
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                // 1. Check if we already have this agent in memory
                if (agentCache.containsKey(modelName)) {
                    System.out.println("⚡ Using cached agent for: " + modelName);
                    agent = agentCache.get(modelName);
                } else {
                    // 2. If not, create it freshly
                    System.out.println("🔄 Creating NEW agent for: " + modelName);
                    
                    SuperAgentFactory factory = new SuperAgentFactory("http://localhost:11434", modelName);
                    Assistant newAgent;

                    if (modelName.equals("tinyllama")) {
                        newAgent = factory.createSuperAgent("src/main/resources/my_docs");
                    } else {                
                        newAgent = factory.createSuperAgent("src/main/resources/my_docs", new WeatherTool(), new CalculatorTool());
                    }

                    // 3. Store in cache and set as active
                    agentCache.put(modelName, newAgent);
                    agent = newAgent;
                }
                return null;
            }

            @Override
            protected void done() {
                appendMessage("--- 🔄 System: Switched to " + modelName + " ---\n");
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                inputField.requestFocus();
            }
        };
        worker.execute();
    }

    private void sendMessage(ActionEvent e) {
        String userText = inputField.getText().trim();
        if (userText.isEmpty()) return;

        appendMessage("You: " + userText + "\n");
        inputField.setText("");
        inputField.setEnabled(false);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try {
                    return agent.chat(userText);
                } catch (Exception ex) {
                    return "Error: " + ex.getMessage();
                }   
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    appendMessage("Agent (" + modelSelector.getSelectedItem() + "): " + response + "\n\n");
                } catch (Exception ex) {
                    appendMessage("Error processing response.\n");
                } finally {
                    inputField.setEnabled(true);
                    inputField.requestFocus();
                }
            }
        };
        worker.execute();
    }

    private void appendMessage(String text) {
        chatArea.append(text);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
}