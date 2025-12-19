package com.generic.tools.ai;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
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

    private static final long serialVersionUID = 1L;
    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JComboBox<String> modelSelector;
    private final JLabel pathLabel; // Shows selected path
    
    private final Map<String, Assistant> agentCache = new HashMap<>();
    private Assistant agent; 
    
    // Store user's selected path
    private String currentDocPath = null; 

    public DesktopChatApp(String docPath) {
        this();
        currentDocPath = docPath;
    }
    
    public DesktopChatApp() {
        super("AI Super Agent (Local)");
        setSize(850, 600); // Slightly wider for path label
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- 1. TOP PANEL (Model & File Selection) ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // Model Selector
        JLabel modelLabel = new JLabel("Model:");
        String[] models = {"gpt-oss:20b-cloud", "llama3.2", "mistral", "tinyllama"};
        modelSelector = new JComboBox<>(models);
        modelSelector.addActionListener(e -> reloadAgent()); // Calls reload without args

        // Browse Button
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
                pathLabel.setText("Docs: " + selectedDir.getName()); // Show short name
                
                // IMPORTANT: Clear cache because the "brain" changed!
                agentCache.clear();
                System.out.println("🗑️ Cache cleared (New documents selected).");
                
                // Trigger reload immediately
                reloadAgent(); 
            }
        });

        topPanel.add(modelLabel);
        topPanel.add(modelSelector);
        topPanel.add(browseButton);
        topPanel.add(pathLabel);
        
        add(topPanel, BorderLayout.NORTH);

        // --- 2. CENTER PANEL (Chat) ---
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

        // --- 4. LISTENERS ---
        sendButton.addActionListener(this::sendMessage);
        inputField.addActionListener(this::sendMessage);

        // Initial Load (Will start without docs)
        reloadAgent();
    }

    private void reloadAgent() {
        String modelName = (String) modelSelector.getSelectedItem();
        
        // DISABLE UI
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        inputField.setText("Initializing " + modelName + "...");

        SwingWorker<Assistant, Void> worker = new SwingWorker<>() {
            @Override
            protected Assistant doInBackground() throws Exception {
                // If path hasn't changed, we can use cache
                // Note: We cleared the cache in the Browse button listener if path changed
                if (agentCache.containsKey(modelName)) {
                    System.out.println("⚡ Using cached agent for: " + modelName);
                    return agentCache.get(modelName);
                }

                System.out.println("🔄 Creating NEW agent for: " + modelName + " with docs: " + currentDocPath);
                SuperAgentFactory factory = new SuperAgentFactory("http://localhost:11434", modelName);
                
                if(currentDocPath == null || currentDocPath.length() == 0) {
                    currentDocPath = "src/main/resources/my_docs";
                }
                
                // Inject tools and the selected path
                // If currentDocPath is null, factory will handle it safely
                return factory.createSuperAgent(currentDocPath, new WeatherTool(), new CalculatorTool());
            }

            @Override
            protected void done() {
                try {
                    Assistant loadedAgent = get();
                    agent = loadedAgent;
                    agentCache.put(modelName, loadedAgent);
                    
                    String status = "Ready (" + modelName + ")";
                    if (currentDocPath != null) status += " + Docs Loaded";
                    else status += " (Chat Mode)";
                    
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