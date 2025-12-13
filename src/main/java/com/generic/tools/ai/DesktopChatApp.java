package com.generic.tools.ai;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;

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
    private final JComboBox<String> modelSelector; // <--- NEW: Dropdown
    
    // Agent is no longer 'final' because we change it dynamically
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
        
        // Define your models here
        String[] models = {"gpt-oss:20b-cloud", "llama3.2"};
        modelSelector = new JComboBox<>(models);
        
        // Add Action Listener: Re-load agent when model changes
        modelSelector.addActionListener(e -> {
            String selectedModel = (String) modelSelector.getSelectedItem();
            reloadAgent(selectedModel);
        });

        topPanel.add(modelLabel);
        topPanel.add(modelSelector);
        add(topPanel, BorderLayout.NORTH); // Add to top of window

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

        // Initialize with the default selection (first item in list)
        reloadAgent(models[0]);
    }

    /**
     * Helper method to create a new Agent with the specific model name
     */
    private void reloadAgent(String modelName) {
        // Disable UI while loading (Optional UX improvement)
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        
        // Run in background to prevent freezing GUI
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                System.out.println("🔄 Switching to model: " + modelName);
                
                // Instantiate Factory with the NEW model name
                SuperAgentFactory factory = new SuperAgentFactory("http://localhost:11434", modelName);
                
                // Re-create the agent
                // Note: We inject the tools here every time we reload
                agent = factory.createSuperAgent("src/main/resources/my_docs", new WeatherTool(), new CalculatorTool());
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