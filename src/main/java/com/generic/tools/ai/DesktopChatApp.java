package com.generic.tools.ai;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.OllamaHelper; 
import com.generic.tools.ai.tools.CalculatorTool;
import com.generic.tools.ai.tools.WeatherTool;

public class DesktopChatApp extends JFrame {

    private static final long serialVersionUID = 1L;
    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JComboBox<String> modelSelector;
    private final JLabel pathLabel;
    
    private final Map<String, Assistant> agentCache = new HashMap<>();
    private Assistant agent; 
    
    private String currentDocPath = null; 
    
    

    public DesktopChatApp() {
        super("AI Super Agent (Hybrid)");
        setSize(850, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- 1. TOP PANEL ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
       JLabel modelLabel = new JLabel("Model:");
        
        // UPDATED: Simplest possible names first
       String[] models = {
                "gpt-oss:20b-cloud",       // Local (Ollama)
                "llama3.2",               // Local (Ollama)
                "gemini-2.5-pro",         // ✅ NEW: Your smartest available model
                "gemini-2.5-flash",       // ✅ NEW: Fast & efficient
                "gemini-2.0-flash",       // ✅ Stable 2.0 version
                "gemini-2.0-flash-exp",   // Experimental 2.0
                "gpt-4o",                 // OpenAI
                "gpt-3.5-turbo"           // OpenAI
        };
        modelSelector = new JComboBox<>(models);
        modelSelector.addActionListener(e -> reloadAgent());

        JButton browseButton = new JButton("📂 Select Docs Folder");
        pathLabel = new JLabel("No folder selected");
        pathLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = chooser.showOpenDialog(this);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedDir = chooser.getSelectedFile();
                currentDocPath = selectedDir.getAbsolutePath();
                pathLabel.setText("Docs: " + selectedDir.getName());
                agentCache.clear();
                reloadAgent(); 
            }
        });

        topPanel.add(modelLabel);
        topPanel.add(modelSelector);
        topPanel.add(browseButton);
        topPanel.add(pathLabel);
        
        add(topPanel, BorderLayout.NORTH);

        // --- 2. CENTER PANEL ---
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        add(scrollPane, BorderLayout.CENTER);

        // --- 3. BOTTOM PANEL ---
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        // --- 4. LISTENERS ---
        sendButton.addActionListener(this::sendMessage);
        inputField.addActionListener(this::sendMessage);
        
        setFonts();

        reloadAgent();
    }
    
    private void setFonts() {
        // --- 1. SMART FONT SELECTION ---
        // "Nirmala UI" is built into Windows 10/11 for Hindi/Kannada/Telugu etc.
        // "Dialog" is a Java logical font that auto-finds the right language font on Linux/Mac.
        String fontName = "Dialog"; // Default fallback
        java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();

        for (String f : availableFonts) {
            if (f.equals("Nirmala UI")) {
                fontName = "Nirmala UI"; // Best for Windows Indian scripts
                break;
            } else if (f.equals("Arial Unicode MS")) {
                fontName = "Arial Unicode MS"; // Good fallback
            }
        }

        Font unicodeFont = new Font(fontName, Font.PLAIN, 14);

        // --- 2. APPLY FONT ---
        chatArea.setFont(unicodeFont);
        inputField.setFont(unicodeFont);
    }

    private void reloadAgent() {
        String modelName = (String) modelSelector.getSelectedItem();
        
        // --- KEY LOGIC ---
        boolean isLocal = OllamaHelper.isOllamaModel(modelName);
        String apiKeyCandidate = null;

        if (!isLocal) {
            String envVar = modelName.contains("gemini") ? "GOOGLE_AI_GEMINI_API_KEY" : "OPENAI_API_KEY";
            apiKeyCandidate = System.getenv(envVar);
            
            if (apiKeyCandidate == null || apiKeyCandidate.isEmpty()) {
                String provider = modelName.contains("gemini") ? "Google Gemini" : "OpenAI";
                apiKeyCandidate = JOptionPane.showInputDialog(this, 
                        "API Key required for " + modelName + ".\nPlease enter your " + provider + " Key:", 
                        "Cloud Model Detected", 
                        JOptionPane.QUESTION_MESSAGE);
            }
            
            if (apiKeyCandidate == null || apiKeyCandidate.isEmpty()) {
                appendMessage("❌ Load Cancelled: No API Key provided.\n");
                return;
            }

            // --- DEBUG: IF GEMINI, LIST AVAILABLE MODELS ---
            // This runs ONCE to tell you exactly what your key can see
            if (modelName.contains("gemini")) {
                listAvailableGoogleModels(apiKeyCandidate);
            }
        }
        
        final String finalApiKey = apiKeyCandidate;

        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        inputField.setText("Initializing " + modelName + "...");

        SwingWorker<Assistant, Void> worker = new SwingWorker<>() {
            @Override
            protected Assistant doInBackground() throws Exception {
                if (agentCache.containsKey(modelName)) {
                    return agentCache.get(modelName);
                }

                SuperAgentFactory factory = new SuperAgentFactory(
                        "http://localhost:11434", 
                        modelName,
                        finalApiKey 
                );
                
                return factory.createSuperAgent(currentDocPath, new WeatherTool(), new CalculatorTool());
            }

            @Override
            protected void done() {
                try {
                    Assistant loadedAgent = get();
                    agent = loadedAgent;
                    agentCache.put(modelName, loadedAgent);
                    
                    String status = "Ready (" + modelName + ")";
                    if (currentDocPath != null) status += " + Docs";
                    
                    appendMessage("--- ✅ System: " + status + " ---\n");
                    inputField.setText("");
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                    inputField.requestFocus();
                } catch (Exception e) {
                    e.printStackTrace();
                    appendMessage("❌ Error loading: " + e.getMessage() + "\n");
                    inputField.setText("Error.");
                }
            }
        };
        worker.execute();
    }

    // --- HELPER: Debug Tool to Find Correct Model Name ---
    private void listAvailableGoogleModels(String apiKey) {
        System.out.println("\n🔍 DEBUG: Checking valid Google models for your key...");
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey))
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    System.out.println("✅ GOOGLE API CONNECTED! Valid models:");
                    // Simple logic to print names from JSON without full parser
                    String body = response.body();
                    String[] lines = body.split("\n");
                    for (String line : lines) {
                        if (line.trim().startsWith("\"name\":")) {
                            System.out.println("   " + line.trim().replace("models/", "").replace(",", "").replace("\"", ""));
                        }
                    }
                    System.out.println("----------------------------------------\n");
                } else {
                    System.err.println("❌ API ERROR (" + response.statusCode() + "): " + response.body());
                    if (response.statusCode() == 400 || response.statusCode() == 403) {
                         System.err.println("👉 ACTION: Enable 'Generative Language API' in Google Cloud Console.");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendMessage(ActionEvent e) {
        String userText = inputField.getText().trim();
        if (userText.isEmpty()) return;

        if (agent == null) {
            appendMessage("⚠️ Agent is initializing...\n");
            return;
        }

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
                    appendMessage("Agent: " + response + "\n\n");
                } catch (Exception ex) {
                    appendMessage("Error processing response.\n" + ex);
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