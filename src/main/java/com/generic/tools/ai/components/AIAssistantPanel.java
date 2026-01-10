package com.generic.tools.ai.components;

import com.generic.tools.ai.SuperAgentFactory;
import com.generic.tools.ai.agent.Assistant;
import java.awt.BorderLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 *
 * @author vivek
 */
public class AIAssistantPanel extends javax.swing.JPanel {

    private static final Logger LOG = Logger.getLogger(AIAssistantPanel.class.getName());

    private String model;
    private String knowledgeBase;
    private String response;

    // 1. Thread Management for Cancellation
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private Future<?> currentTask;
    
    private final Map<String, Assistant> agentCache = new HashMap<>();
    private Assistant agent;

    private SwingWorker<String, Void> sendMessageWorker;

    private List<String> models = Arrays.asList(
            "tinyllama",
            "gemma3"
    );
    
    private final List<Object> externalTools = new ArrayList<>(); 
    private String customSystemRules = "";
        
    
    /**
     * Default Constructor (No extra tools initially)
     */
    public AIAssistantPanel() {
        this(null, null); // Call the main constructor
    }
    
    /**
     * Creates new form AIAssistantPanel
     */
    public AIAssistantPanel(List<Object> additionalTools, String extraRules) {
        initComponents();
        
        if (additionalTools != null) {
            this.externalTools.addAll(additionalTools);
        }
        
        // Store specific rules for these tools (e.g., "Use FileTool for...")
        if (extraRules != null) {
            this.customSystemRules = extraRules;
        }
        
        model = (String) modelList.getSelectedItem();
        newAgent();
        
        // 2. Initialize Controller Logic (Wire up Send/Stop)
        initControllerLogic();
    }
    
    // 1. Add a token tracker to your class fields
    private Object activeRequestToken = new Object(); 

    private void initControllerLogic() {
        // --- HANDLE SEND ---
        inputPanel.setOnSend(userText -> {
            if (agent == null) {
                messagesPanel.addAgentMessage("Agent is initializing...");
                inputPanel.setProcessing(false); 
                return;
            }

            messagesPanel.addUserMessage(userText);
            inputPanel.setPlaceholder("Thinking...");
            
            // 2. Generate a unique ID for THIS specific request
            final Object myRequestToken = new Object();
            activeRequestToken = myRequestToken;

            // 3. Submit the task
            currentTask = executor.submit(() -> {
                try {
                    if (Thread.currentThread().isInterrupted()) return;

                    // BLOCKING CALL
                    String response = agent.chat(userText);
                    
                    // 4. UI Update with IDENTITY CHECK
                    SwingUtilities.invokeLater(() -> {
                        // 🛑 CRITICAL CHECK:
                        // "Is the global token still MY token?"
                        // If user clicked Stop or Send again, activeRequestToken will be different.
                        if (activeRequestToken != myRequestToken) {
                            LOG.info("Discarding response from old/cancelled task.");
                            return; 
                        }

                        messagesPanel.addAgentMessage(response);
                        onTaskComplete();
                    });

                } catch (Exception e) {
                    // Check if it was a real error or just a stop
                    boolean isInterrupted = e instanceof InterruptedException || 
                                            Thread.currentThread().isInterrupted();
                    
                    if (!isInterrupted) {
                        String errorMsg = handleException(e);
                        SwingUtilities.invokeLater(() -> {
                            // Same Identity Check for Errors
                            if (activeRequestToken != myRequestToken) return;
                            
                            messagesPanel.addAgentMessage("Error: " + errorMsg);
                            onTaskComplete();
                        });
                    }
                }
            });
        });

        // --- HANDLE STOP ---
        inputPanel.setOnStop(() -> {
            // 5. Invalidate the current token immediately
            // This ensures any running tasks will fail the "== myRequestToken" check
            activeRequestToken = new Object(); 
            
            if (currentTask != null && !currentTask.isDone()) {
                currentTask.cancel(true); // Try to kill the thread
                messagesPanel.addAgentMessage(" 🛑 [Stopped by User]");
            }
            onTaskComplete();
        });
    }
    
    // Helper to reset UI state
    private void onTaskComplete() {
        inputPanel.setProcessing(false); // Switch 'Stop' back to 'Send'
        inputPanel.clearInput();
        inputPanel.setEnabledInput(true);
        inputPanel.focusInput();
    }
       
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        addModelBtn = new javax.swing.JButton();
        deleteModelBtn = new javax.swing.JButton();
        messagesPanel = new com.generic.tools.ai.components.ChatMessagesPanel();
        jPanel1 = new javax.swing.JPanel();
        modelList = new javax.swing.JComboBox<>();
        jButton1 = new javax.swing.JButton();
        path = new javax.swing.JLabel();
        inputPanel = new com.generic.tools.ai.components.ChatInputPanel();

        addModelBtn.setText("Add Model");
        addModelBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addModelBtnActionPerformed(evt);
            }
        });

        deleteModelBtn.setText("Delete Model");
        deleteModelBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteModelBtnActionPerformed(evt);
            }
        });

        setLayout(new java.awt.BorderLayout());
        add(messagesPanel, java.awt.BorderLayout.CENTER);

        jPanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        modelList.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "gpt-oss:20b-cloud", "llama3.2", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gpt-4o", "gpt-3.5-turbo" }));
        modelList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                modelListActionPerformed(evt);
            }
        });
        jPanel1.add(modelList);

        jButton1.setText("📂 Select Docs Folder");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton1);

        path.setText("No Docs Selected");
        jPanel1.add(path);

        add(jPanel1, java.awt.BorderLayout.PAGE_START);

        inputPanel.setPreferredSize(new java.awt.Dimension(324, 80));
        add(inputPanel, java.awt.BorderLayout.PAGE_END);
        inputPanel.setOnSend(userText -> {
            messagesPanel.addUserMessage(userText);
            inputPanel.setEnabledInput(false);
            inputPanel.setPlaceholder("Thinking...");

            new Thread(() -> {

                try {
                    response = agent.chat(userText);
                }catch(Exception e) {
                    response = handleException(e);
                }
                SwingUtilities.invokeLater(() -> {
                    messagesPanel.addAgentMessage(response);
                    inputPanel.clearInput();
                    inputPanel.setEnabledInput(true);
                    inputPanel.focusInput();
                });
            }).start();
        });
    }// </editor-fold>//GEN-END:initComponents

    private void modelListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modelListActionPerformed
        model = (String) modelList.getSelectedItem();
        newAgent();
    }//GEN-LAST:event_modelListActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        getKnowledgeBase();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void addModelBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addModelBtnActionPerformed
        openAddDialog(null, modelList);
    }//GEN-LAST:event_addModelBtnActionPerformed

    private void deleteModelBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteModelBtnActionPerformed
         int index = modelList.getSelectedIndex();
         if (index != -1) {
            modelList.removeItemAt(index);
        } 
    }//GEN-LAST:event_deleteModelBtnActionPerformed

    private void openAddDialog(JFrame parent, JComboBox<String> comboBox) {
        JDialog dialog = new JDialog(parent, "Add Model", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(300, 125);

        JTextField textField = new JTextField();
        JPanel buttonPanel = new JPanel();

        JButton add = new JButton("Add");
        JButton cancel = new JButton("Cancel");

        add.addActionListener(e -> {
            String text = textField.getText().trim();
            if (!text.isEmpty()) {
                comboBox.addItem(text);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "Please enter text");
            }
        });

        cancel.addActionListener(e -> dialog.dispose());

        buttonPanel.add(add);
        buttonPanel.add(cancel);

        dialog.add(new JLabel("Enter Model Name:"), BorderLayout.NORTH);
        dialog.add(textField, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
    
    private void newAgent() {
        SwingWorker<Assistant, Void> worker = new SwingWorker<Assistant, Void>() {
            @Override
            protected Assistant doInBackground() throws Exception {
                if (agentCache.containsKey(model)) {
                    LOG.info(() -> "Found cached model " + model);
                    messagesPanel.addAgentMessage("Switching to model : " + model + "\n");
                    return agentCache.get(model);
                }
                messagesPanel.addAgentMessage(
                    "Create new instance of model " + model +
                    (knowledgeBase != null ? " with docs = " + knowledgeBase : "") +
                    "\n"
                );
                SuperAgentFactory factory = new SuperAgentFactory("http://localhost:11434", model);
                
                
                String fullSystemPrompt = SuperAgentFactory.DEFAULT_SYSTEM_PROMPT;
                if (!customSystemRules.isEmpty()) {
                    fullSystemPrompt += "\n" + customSystemRules;
                }
                
                return factory.createSuperAgent(
                    knowledgeBase, 
                    fullSystemPrompt, 
                    externalTools.toArray() 
                );
            }

            @Override
            protected void done() {
                try {
                    agent = get();
                    agentCache.put(model, agent);
                    inputPanel.setEnabledInput(true);
                    inputPanel.clearInput();
                } catch (InterruptedException | ExecutionException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }

        };
        worker.execute();
    }

    public void getKnowledgeBase() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            knowledgeBase = selectedDir.getAbsolutePath();
            path.setText("Docs: " + selectedDir.getName()); // Show short name

            // IMPORTANT: Clear cache because the "brain" changed!
            agentCache.clear();
            messagesPanel.addAgentMessage("🗑️ Cache cleared (New documents selected).");
            messagesPanel.addAgentMessage("\n");
        }

        newAgent();
    }
    
  
    /**
     * Helper to clean up ugly JSON error messages from APIs
     */
    private String handleException(Exception ex) {
        // 1. Get the real root cause
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        String rawMsg = cause.getMessage();
        
        if (rawMsg == null) return "Unknown error occurred.";

        // 2. Check for common JSON error pattern: "message": "THE REAL ERROR"
        if (rawMsg.contains("\"message\":")) {
            try {
                // Quick-and-dirty Regex to find the message content inside quotes
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"message\":\\s*\"(.*?)\"");
                java.util.regex.Matcher m = p.matcher(rawMsg);
                if (m.find()) {
                    return m.group(1); // Return just the inner text
                }
            } catch (Exception ignored) {}
        }

        // 3. Fallback: If it's a timeout or simple error, return it directly
        if (rawMsg.contains("timed out")) return "Connection timed out. Server is slow.";
        if (rawMsg.contains("404")) return "Model not found or API disabled.";
        
        return rawMsg; // Return raw if we can't clean it
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addModelBtn;
    private javax.swing.JButton deleteModelBtn;
    private com.generic.tools.ai.components.ChatInputPanel inputPanel;
    private javax.swing.JButton jButton1;
    private javax.swing.JPanel jPanel1;
    private com.generic.tools.ai.components.ChatMessagesPanel messagesPanel;
    private javax.swing.JComboBox<String> modelList;
    private javax.swing.JLabel path;
    // End of variables declaration//GEN-END:variables
}
