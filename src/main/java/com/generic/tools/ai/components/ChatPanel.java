package com.generic.tools.ai.components;

import com.generic.tools.ai.helper.ChatHistoryManager;
import com.generic.tools.ai.helper.ChatSession;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.SwingUtilities;


public class ChatPanel extends javax.swing.JPanel {
    
    private Map<String, ChatMessageWindow> chatPanels = new HashMap<>();
    
    private final static String NEW_CHAT = "$_NEW_CHAT_"; 
    private List<Object> additionalTools;
    private String extraRules;
    
    private int counter;
    
    private ChatMessageWindow currentMessageWindow;
    
    private volatile Object activeRequestToken;
    private Map<ChatMessageWindow, Future<?>> activeWindowTasks = new HashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();    
    
    private String currentQuery;    
    
    private Map<ChatMessageWindow, Boolean> windowBusyState = new HashMap<>();
    
    /**
     * Creates new form ChatPanel
     */
    public ChatPanel() {
       this(null, "");
    }
    
    public ChatPanel(List<Object> additionalTools, String extraRules) {
        this.additionalTools = additionalTools;
        this.extraRules = extraRules;
        initComponents();

        // 1. Run restoration logic on the UI Thread specifically
        javax.swing.SwingUtilities.invokeLater(() -> {
            restoreChatHistory();
        });

        initChatHandlers();
        initListeners();

        // Auto-save on exit
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveAllChats));
    }

    private void restoreChatHistory() {
        List<ChatSession> savedSessions = ChatHistoryManager.loadChats();

        if (savedSessions != null && !savedSessions.isEmpty()) {
            System.out.println("📂 Loading " + savedSessions.size() + " chats...");

            // Clear defaults
            chatPanels.clear();
            // Note: verify your UIList has a clear method. If not, ignore clearing for now.
            // list.removeAll(); 

            String lastChatName = null;

            for (ChatSession session : savedSessions) {
                // A. Create Window
                ChatMessageWindow window = createChatWindow(session.modelName);

                // B. Restore Bubbles (Visuals)
                window.getChatMessagesPanel().loadHistory(session.messages);

                // C. Register in Map
                chatPanels.put(session.name, window);

                // D. Add to List
                list.addItem(session.name);

                lastChatName = session.name;
            }

            // 3. CRITICAL: Force the switch to the last chat
            if (lastChatName != null) {
                System.out.println("🔄 Switching to last chat: " + lastChatName);
                list.setSelectedItem(lastChatName); // Update List Selection
                updateChatMessageWindow(lastChatName); // Force Panel Swap
            }

            counter = savedSessions.size() + 1;
        } else {
            System.out.println("📂 No history found. Starting new.");
            initChatWindow();
        }
    }
  
    private void initChatWindow() {
        // 1. Get the initial key (e.g., "$_NEW_CHAT_")
        String initialKey = (String) list.getSelectedItem();
        String model = (String) modelList.getSelectedItem();

        // 2. Create the logical window
        currentMessageWindow = createChatWindow(model);
        chatPanels.put(initialKey, currentMessageWindow);

        // 3. CRITICAL FIX: Immediately replace the "dummy" messagesPanel created
        // in initComponents with the actual panel from the new Window.
        // This prevents typing into a "Ghost Panel".
        updateChatMessageWindow(initialKey);
        
    }
    
    private ChatMessageWindow createChatWindow(String name) {
        
        ChatMessagesPanel messagesPanel = new ChatMessagesPanel();
        
        ChatMessageWindow window = new ChatMessageWindow(
                name,
                "",
                messagesPanel,
                additionalTools,
                extraRules
        );

        return window;
    }
    
    private void initListeners() {
        list.getList().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedValue = list.getList().getSelectedValue();
                if (selectedValue != null) {
                    updateChatMessageWindow(selectedValue);
                }
            }
        });
    }    
    
    private void initChatHandlers() {

        inputPanel.setOnSend(userText -> {
            currentQuery = userText;
            if (currentMessageWindow == null || currentMessageWindow.getChatAgent() == null) {
                messagesPanel.addAgentMessage("Agent is initializing...");
                inputPanel.setProcessing(false);
                return;
            }

            // --- 1. RENAME LOGIC START ---
            // Get the currently selected name from the list
            String currentListName = list.getList().getSelectedValue();
            
            // Check if this is a generic "New Chat" that needs renaming
            if (currentListName != null && currentListName.contains(NEW_CHAT)) {
                
                // A. Generate new name (Truncate to ~25 chars)
                String newName = userText.trim();
                if (newName.length() > 25) {
                    newName = newName.substring(0, 25) + "...";
                }

                // B. Handle Duplicates (e.g., if you type "Hello" in two different chats)
                int dupCounter = 1;
                String originalNewName = newName;
                while (chatPanels.containsKey(newName)) {
                    newName = originalNewName + " (" + (++dupCounter) + ")";
                }

                // C. Update the Map: Move the window object to the new key
                ChatMessageWindow windowRef = chatPanels.remove(currentListName);
                chatPanels.put(newName, windowRef);

                // D. Update the UI List
                // We assume UIList wraps a JList with a DefaultListModel
                javax.swing.DefaultListModel<String> model = 
                    (javax.swing.DefaultListModel<String>) list.getList().getModel();
                
                int index = list.getList().getSelectedIndex();
                if (index != -1) {
                    model.set(index, newName); // Replaces the text at this index
                }
            }
            // --- RENAME LOGIC END ---

            messagesPanel.addUserMessage(userText);

            // ... (Rest of your existing logic below is unchanged) ...
            
            windowBusyState.put(currentMessageWindow, true);
            inputPanel.setProcessing(true);
            inputPanel.setPlaceholder("Thinking...");
            
            final Object myRequestToken = new Object();
            activeRequestToken = myRequestToken;
            final ChatMessageWindow targetWindow = currentMessageWindow;

            Future<?> newTask = executor.submit(()
                    -> runAgentTask(userText, myRequestToken, targetWindow)
            );
            activeWindowTasks.put(targetWindow, newTask);
        });

        inputPanel.setOnStop(this::stopCurrentTask);
    }
 
    private void runAgentTask(String userText, Object myRequestToken, ChatMessageWindow targetWindow) {
        try {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            String response = targetWindow.getChatAgent().chat(userText);

            uiUpdate(myRequestToken, () -> {
                targetWindow.getChatMessagesPanel().addAgentMessage(response);

                // Cleanup state
                windowBusyState.put(targetWindow, false);
                activeWindowTasks.remove(targetWindow); // Remove from task map

                // Only reset UI if we are looking at this window
                if (currentMessageWindow == targetWindow) {
                    onTaskComplete();
                }
            });

        } catch (Exception e) {
            // ... (Error handling same as above, ensure activeWindowTasks.remove is called) ...
            boolean interrupted = e instanceof InterruptedException || Thread.currentThread().isInterrupted();
            if (interrupted) {
                return;
            }

            uiUpdate(myRequestToken, () -> {
                targetWindow.getChatMessagesPanel().addAgentMessage("Error: " + e.getMessage());
                windowBusyState.put(targetWindow, false);
                activeWindowTasks.remove(targetWindow);
                if (currentMessageWindow == targetWindow) {
                    onTaskComplete();
                }
            });
        }
    }

    private void stopCurrentTask() {
        // 1. Get the task specifically for the CURRENT window
        Future<?> task = activeWindowTasks.get(currentMessageWindow);

        // 2. Cancel it if it exists
        if (task != null && !task.isDone()) {
            task.cancel(true);
            messagesPanel.addAgentMessage(" 🛑 [Stopped by User]");
        }

        // 3. Cleanup
        activeWindowTasks.remove(currentMessageWindow);
        windowBusyState.put(currentMessageWindow, false);

        // 4. Reset UI immediately
        onTaskComplete();
        inputPanel.setPlaceholder(currentQuery);
    }

    private void uiUpdate(Object token, Runnable uiTask) {
        SwingUtilities.invokeLater(() -> {
            // Check if this specific request is still valid
            if (activeRequestToken != token) {
                return;
            }
            uiTask.run();
        });
    }

    private void onTaskComplete() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::onTaskComplete);
            return;
        }

        inputPanel.setProcessing(false);
        inputPanel.clearInput();
        inputPanel.setEnabledInput(true);
        inputPanel.focusInput();
    }

    private void updateChatMessageWindow(String key) {
        System.out.println("🖼️ Updating View for: " + key);
        ChatMessageWindow newWindow = chatPanels.get(key);

        if (newWindow == null) {
            System.err.println("❌ ERROR: No window found for key: " + key);
            return;
        }

        // 1. Remove the OLD panel strictly
        if (messagesPanel != null) {
            leftPanel.remove(messagesPanel);
        }

        // 2. Update reference
        currentMessageWindow = newWindow;
        messagesPanel = currentMessageWindow.getChatMessagesPanel();

        // 3. Add NEW panel
        leftPanel.add(messagesPanel, java.awt.BorderLayout.CENTER);

        // 4. Input State Logic (Keep your existing logic here)
        boolean isBusy = windowBusyState.getOrDefault(currentMessageWindow, false);
        if (isBusy) {
            inputPanel.setProcessing(true);
            inputPanel.setPlaceholder("Thinking...");
        } else {
            inputPanel.setProcessing(false);
            inputPanel.setPlaceholder("");
            inputPanel.setEnabledInput(true);
        }

        // 5. CRITICAL: Force Layout Refresh on the Parent
        leftPanel.revalidate(); // Recalculate layout
        leftPanel.repaint();    // Redraw pixels
    }
    
    // Add this helper method in ChatPanel
    private void saveAllChats() {
        List<ChatSession> sessionsToSave = new ArrayList<>();

        // Iterate over the Map of chat panels
        for (Map.Entry<String, ChatMessageWindow> entry : chatPanels.entrySet()) {
            String chatName = entry.getKey();
            ChatMessageWindow window = entry.getValue();

            // Create session object
            ChatSession session = new ChatSession(chatName, window.getModel()); // Ensure ChatMessageWindow has getModelName()

            // Extract messages
            session.messages = window.getChatMessagesPanel().getMessageHistory();

            sessionsToSave.add(session);
        }

        ChatHistoryManager.saveChats(sessionsToSave);
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        splitPane = new javax.swing.JSplitPane();
        rightPanel = new javax.swing.JPanel();
        newChatBtn = new javax.swing.JButton();
        list = new com.generic.tools.components.list.UIList();
        leftPanel = new javax.swing.JPanel();
        messagesPanel = new com.generic.tools.ai.components.ChatMessagesPanel();
        inputPanel = new com.generic.tools.ai.components.ChatInputPanel();
        topPanel = new javax.swing.JPanel();
        modelList = new javax.swing.JComboBox<>();

        setLayout(new java.awt.BorderLayout());

        splitPane.setDividerSize(1);
        splitPane.setResizeWeight(1.0);

        rightPanel.setLayout(new javax.swing.BoxLayout(rightPanel, javax.swing.BoxLayout.PAGE_AXIS));

        newChatBtn.setText("New Chat");
        newChatBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newChatBtnActionPerformed(evt);
            }
        });
        rightPanel.add(newChatBtn);
        rightPanel.add(list);
        list.addItem("_"+NEW_CHAT+"_");
        list.setSelectedItem("_"+NEW_CHAT+"_");

        splitPane.setRightComponent(rightPanel);

        leftPanel.setLayout(new java.awt.BorderLayout());
        leftPanel.add(messagesPanel, java.awt.BorderLayout.CENTER);
        leftPanel.add(inputPanel, java.awt.BorderLayout.PAGE_END);

        topPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        modelList.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "gpt-oss:20b-cloud", "qwen3-coder:480b-cloud", "llama3.2", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gpt-4o", "gpt-3.5-turbo" }));
        modelList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                modelListActionPerformed(evt);
            }
        });
        topPanel.add(modelList);

        leftPanel.add(topPanel, java.awt.BorderLayout.PAGE_START);

        splitPane.setLeftComponent(leftPanel);

        add(splitPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void newChatBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newChatBtnActionPerformed
        String chatName = NEW_CHAT + (++counter); 
        list.addItem(chatName);
        chatPanels.put(chatName, createChatWindow((String) modelList.getSelectedItem()));
    }//GEN-LAST:event_newChatBtnActionPerformed

    private void modelListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modelListActionPerformed
       currentMessageWindow.setModel((String) modelList.getSelectedItem());
       currentMessageWindow.newAgent(); 
    }//GEN-LAST:event_modelListActionPerformed

      

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.generic.tools.ai.components.ChatInputPanel inputPanel;
    private javax.swing.JPanel leftPanel;
    private com.generic.tools.components.list.UIList list;
    private com.generic.tools.ai.components.ChatMessagesPanel messagesPanel;
    private javax.swing.JComboBox<String> modelList;
    private javax.swing.JButton newChatBtn;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JSplitPane splitPane;
    private javax.swing.JPanel topPanel;
    // End of variables declaration//GEN-END:variables
}
