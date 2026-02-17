package com.generic.tools.ai.components;

import com.generic.tools.ai.TitleGenerator;
import com.generic.tools.ai.helper.ChatHistoryManager;
import com.generic.tools.ai.helper.ChatSession;
import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;


public class ChatPanel extends javax.swing.JPanel {
    
    // --- State Variables ---
    private Map<String, ChatMessageWindow> chatPanels = new HashMap<>();
    private Map<ChatMessageWindow, Boolean> windowBusyState = new HashMap<>();
    private Map<ChatMessageWindow, Future<?>> activeWindowTasks = new HashMap<>();
    
    private javax.swing.JPanel cardsPanel;     
    private java.awt.CardLayout cardLayout;  
    private ChatMessagesPanel messagesPanel;
    
    // --- Constants & Config ---
    private final static String NEW_CHAT_PREFIX = "New Chat "; 
    private List<Object> additionalTools;
    private String extraRules;
    private int counter = 0;

    // --- Current State ---
    private ChatMessageWindow currentMessageWindow;
    private volatile Object activeRequestToken;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String currentQuery;
    private String knowledgeBase;

    // --- Constructors ---
    public ChatPanel() {
        this(new ArrayList<>(), "");
    }

    public ChatPanel(List<Object> additionalTools, String extraRules) {
        this.additionalTools = additionalTools;
        this.extraRules = extraRules;
        
        // 1. NetBeans generates the UI here (DO NOT TOUCH)
        initComponents();

        // 2. --- MANUAL FIX STARTS HERE ---
        // Initialize our CardLayout stack
        cardLayout = new java.awt.CardLayout();
        cardsPanel = new javax.swing.JPanel(cardLayout);

        // Add a blank card so the area is never transparent/empty
        javax.swing.JPanel blankCard = new javax.swing.JPanel();
        blankCard.setOpaque(true); // Ensure it paints background
        cardsPanel.add(blankCard, "BLANK");
        
        rightPanel.setMinimumSize(new java.awt.Dimension(0, 0));
        
        // Optional: Allow Left Panel to shrink too
        leftPanel.setMinimumSize(new java.awt.Dimension(0, 0));

        // Add our 'cardsPanel' into the Center slot instead
        leftPanel.add(cardsPanel, java.awt.BorderLayout.CENTER);

        // Force the UI to accept the change
        leftPanel.revalidate();
        leftPanel.repaint();

        // 3. Continue with normal initialization
        initChatHandlers();
        initListeners();
        initListPopupMenu();

        // 4. Start load sequence AND Collapse Right Pane
        javax.swing.SwingUtilities.invokeLater(() -> {
            startupSequence();
            
            // --- ADD THIS LINE ---
            // 1.0 means 100% space to the Left, 0% to the Right
            splitPane.setDividerLocation(1.0); 
            // --------------------
        });

       
    }
    
    // Helper to add a window to the CardLayout stack
    private void registerWindowCard(String name, ChatMessageWindow window) {
        // We get the visual panel from the window and add it to our cardsPanel
        cardsPanel.add(window.getChatMessagesPanel(), name);
    }

    // --- Core Startup Logic ---
    private void startupSequence() {
        List<ChatSession> savedSessions = ChatHistoryManager.loadChats();

        if (savedSessions != null && !savedSessions.isEmpty()) {
            chatPanels.clear();
            // list.removeAll(); // Use if your UIList supports it

            String lastChatName = null;

            for (ChatSession session : savedSessions) {
                // 1. Create the Window Object
                ChatMessageWindow window = createChatWindow(session.modelName);

                // 2. Restore the Bubbles (Visuals)
                window.getChatMessagesPanel().loadHistory(session.messages);

                // 3. REGISTER CARD (The new step)
                registerWindowCard(session.name, window);

                // 4. Update Map and List
                chatPanels.put(session.name, window);
                list.addItem(session.name);

                lastChatName = session.name;
            }

            counter = savedSessions.size() + 1;

            // 5. Show the last chat
            if (lastChatName != null) {
                list.setSelectedItem(lastChatName);
                updateChatMessageWindow(lastChatName);
            }
        } else {
            startNewChat();
        }
    }

    // --- Logic Methods ---

    private void startNewChat() {
        String chatName = "New Chat " + (++counter); // Or your preferred naming
        String model = (String) modelList.getSelectedItem();

        ChatMessageWindow window = createChatWindow(model);

        // REGISTER CARD
        registerWindowCard(chatName, window);

        chatPanels.put(chatName, window);
        list.addItem(chatName);
        list.setSelectedItem(chatName);

        updateChatMessageWindow(chatName);
    }
    
    private ChatMessageWindow createChatWindow(String modelName) {
        ChatMessagesPanel newMsgPanel = new ChatMessagesPanel();
        return new ChatMessageWindow(modelName, knowledgeBase, newMsgPanel, additionalTools, extraRules);
    }

    private void updateChatMessageWindow(String key) {
        ChatMessageWindow newWindow = chatPanels.get(key);
        if (newWindow == null) {
            System.out.println("❌ Error: No window found for key: " + key);
            return;
        }

        currentMessageWindow = newWindow;

        // Update global reference (for other methods that might use it)
        messagesPanel = currentMessageWindow.getChatMessagesPanel();

        // --- SWITCH CARD ---
        // This string 'key' MUST match what was used in cardsPanel.add()
        cardLayout.show(cardsPanel, key);
        // -------------------

        restoreInputState();
    }

    private void restoreInputState() {
        boolean isBusy = windowBusyState.getOrDefault(currentMessageWindow, false);
        if (isBusy) {
            inputPanel.setProcessing(true);
            inputPanel.setPlaceholder("Thinking...");
        } else {
            inputPanel.setProcessing(false);
            inputPanel.setPlaceholder("");
            inputPanel.setEnabledInput(true);
            inputPanel.focusInput();
        }
    }

    // --- Event Handlers ---

    private void initListeners() {
        list.getList().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedKey = list.getList().getSelectedValue();

                // Check if valid and different from what we are currently looking at
                if (selectedKey != null) {
                    // Find the window associated with this key
                    ChatMessageWindow targetWindow = chatPanels.get(selectedKey);

                    // If it's a different window than the active one, switch.
                    if (targetWindow != null && targetWindow != currentMessageWindow) {
                        updateChatMessageWindow(selectedKey);
                    }
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

            // 1. Rename Logic (Optional, keeps chat generic until renamed)
            handleAutoRename(userText);

            // 2. Add User Message to UI
            messagesPanel.addUserMessage(userText);

            // 3. Setup Task
            windowBusyState.put(currentMessageWindow, true);
            inputPanel.setProcessing(true);
            inputPanel.setPlaceholder("Thinking...");

            final Object myRequestToken = new Object();
            activeRequestToken = myRequestToken;
            final ChatMessageWindow targetWindow = currentMessageWindow;

            // 4. Submit Task
            Future<?> newTask = executor.submit(() -> runAgentTask(userText, myRequestToken, targetWindow));
            activeWindowTasks.put(targetWindow, newTask);
        });

        inputPanel.setOnStop(this::stopCurrentTask);
    }

    private void handleAutoRename(String userText) {
        String currentName = list.getList().getSelectedValue();

        // Only generate a smart title if:
        // 1. It is currently a default "New Chat"
        // 2. The user text is meaningful (not empty)
        if (currentName != null && currentName.startsWith(NEW_CHAT_PREFIX) && !userText.trim().isEmpty()) {
            
            // Capture the model to use for generation (use the one currently selected)
            String modelForTitle = (String) modelList.getSelectedItem();
            
            // --- START BACKGROUND TASK ---
            new javax.swing.SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    // This runs on a background thread.
                    // It creates a temporary TitleGenerator just for this task.
                    TitleGenerator generator = new TitleGenerator(modelForTitle);
                    return generator.generateTitle(userText);
                }

                @Override
                protected void done() {
                    try {
                        // This runs on the UI Thread (EDT) when finished.
                        String smartTitle = get(); // Retrieve the result
                        
                        // Perform the actual rename in the UI
                        performRename(currentName, smartTitle);
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                        // If it fails, fallback to simple truncation or keep "New Chat"
                        // Optional: performRename(currentName, userText.substring(0, 15) + "...");
                    }
                }
            }.execute();
        }
    }
    
    private void performRename(String oldName, String newName) {
        // 1. Handle Duplicates
        int dup = 1;
        String base = newName;
        while (chatPanels.containsKey(newName)) {
            newName = base + " (" + (++dup) + ")";
        }

        // Safety check: ensure the old chat still exists (user didn't delete it while waiting)
        if (!chatPanels.containsKey(oldName)) return;

        // 2. Update the Map
        ChatMessageWindow win = chatPanels.remove(oldName);
        chatPanels.put(newName, win);

        // 3. Update the List
        javax.swing.DefaultListModel<String> model = (javax.swing.DefaultListModel<String>) list.getList().getModel();
        int idx = list.getList().getSelectedIndex();
        
        // Find the index of the old name in case selection changed
        if (idx == -1 || !model.get(idx).equals(oldName)) {
            for(int i=0; i<model.size(); i++) {
                if(model.get(i).equals(oldName)) {
                    idx = i;
                    break;
                }
            }
        }
        
        if (idx != -1) {
            model.set(idx, newName);
        }

        // 4. Update CardLayout Registry
        // We add the component again under the new name so CardLayout can find it
        cardsPanel.add(win.getChatMessagesPanel(), newName);
        
        // 5. If this was the active chat, ensure it stays visible
        if (currentMessageWindow == win) {
            cardLayout.show(cardsPanel, newName);
        }
        
        // 6. Persist the change
        saveAllChats();
    }
    
    private void runAgentTask(String userText, Object token, ChatMessageWindow targetWindow) {
        try {
            if (Thread.currentThread().isInterrupted()) return;
            String response = targetWindow.getChatAgent().chat(userText);
            
            uiUpdate(token, () -> {
                targetWindow.getChatMessagesPanel().addAgentMessage(response);
                finalizeTask(targetWindow);
            });
        } catch (Exception e) {
            if (e instanceof InterruptedException) return;
            uiUpdate(token, () -> {
                targetWindow.getChatMessagesPanel().addAgentMessage("Error: " + e.getMessage());
                finalizeTask(targetWindow);
            });
        }
    }

    private void finalizeTask(ChatMessageWindow window) {
        windowBusyState.put(window, false);
        activeWindowTasks.remove(window);
        if (currentMessageWindow == window) {
            onTaskComplete();
        }
    }

    private void stopCurrentTask() {
        Future<?> task = activeWindowTasks.get(currentMessageWindow);
        if (task != null && !task.isDone()) {
            task.cancel(true);
            messagesPanel.addAgentMessage(" 🛑 [Stopped]");
        }
        finalizeTask(currentMessageWindow);
        inputPanel.setPlaceholder(currentQuery);
    }

    private void onTaskComplete() {
        inputPanel.setProcessing(false);
        inputPanel.clearInput();
        inputPanel.setEnabledInput(true);
        inputPanel.focusInput();
    }

    private void uiUpdate(Object token, Runnable task) {
        SwingUtilities.invokeLater(() -> {
            if (activeRequestToken == token) task.run();
        });
    }
    
    public void saveAllChats() {
        
        List<ChatSession> sessionsToSave = new ArrayList<>();

        for (Map.Entry<String, ChatMessageWindow> entry : chatPanels.entrySet()) {
            String chatName = entry.getKey();
            ChatMessageWindow window = entry.getValue();

            ChatSession session = new ChatSession(chatName, window.getModel());
            session.messages = window.getChatMessagesPanel().getMessageHistory();

            sessionsToSave.add(session);
        }
        
        // This method is still responsible for writing to disk
        ChatHistoryManager.saveChats(sessionsToSave);
    }
    
    private void initListPopupMenu() {
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete Chat");

        // Action: What happens when you click "Delete"
        deleteItem.addActionListener(e -> {
            String selected = list.getList().getSelectedValue();
            if (selected != null) {
                deleteChat(selected);
            }
        });

        popup.add(deleteItem);

        // Mouse Listener: Detect Right-Click on the List
        list.getList().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    // Optional: Select the item under the mouse automatically
                    int index = list.getList().locationToIndex(e.getPoint());
                    if (index != -1 && list.getList().getCellBounds(index, index).contains(e.getPoint())) {
                        list.getList().setSelectedIndex(index);
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
    }
    
    private void deleteChat(String chatName) {
        // 1. Get the Window object before we remove it
        ChatMessageWindow windowToRemove = chatPanels.get(chatName);
        if (windowToRemove == null) {
            return;
        }

        // 2. Stop any active tasks (AI generation) for this specific chat
        if (activeWindowTasks.containsKey(windowToRemove)) {
            java.util.concurrent.Future<?> task = activeWindowTasks.get(windowToRemove);
            if (task != null && !task.isDone()) {
                task.cancel(true); // Stop the thread
            }
            activeWindowTasks.remove(windowToRemove);
            windowBusyState.remove(windowToRemove);
        }

        // 3. Determine if we are deleting the one currently on screen
        boolean isDeletingCurrent = (currentMessageWindow == windowToRemove);

        // 4. Remove from Data Structures
        chatPanels.remove(chatName);

        // Remove the visual panel from the CardLayout stack
        cardsPanel.remove(windowToRemove.getChatMessagesPanel());

        // 5. Remove from the UI List
        javax.swing.DefaultListModel<String> model
                = (javax.swing.DefaultListModel<String>) list.getList().getModel();

        int indexToRemove = -1;
        for (int i = 0; i < model.getSize(); i++) {
            if (model.get(i).equals(chatName)) {
                indexToRemove = i;
                break;
            }
        }

        if (indexToRemove != -1) {
            model.remove(indexToRemove);
        }

        // 6. Handle Selection (If we deleted the active chat)
        if (isDeletingCurrent) {
            if (model.getSize() > 0) {
                // Select the previous chat, or the top one
                int newIndex = Math.max(0, indexToRemove - 1);
                String newKey = model.get(newIndex);

                // This will trigger the ListSelectionListener -> updateChatMessageWindow
                list.setSelectedItem(newKey);
            } else {
                // If the list is now empty, create a fresh new chat
                startNewChat();
            }
        }

        // 7. Force UI Refresh
        cardsPanel.revalidate();
        cardsPanel.repaint();

        // 8. Persist changes (Save to file immediately)
        saveAllChats();
    }   
    
    public void themeUpdate() {
        // 1. Identify the active window
        final ChatMessageWindow activeWindow = currentMessageWindow;

        // 2. Update the active panel immediately (Priority)
        if (activeWindow != null && activeWindow.getChatMessagesPanel() != null) {
            activeWindow.getChatMessagesPanel().initStyles();
        }

        // 3. Update background tabs via SwingWorker
        SwingWorker<Void, ChatMessageWindow> worker = new SwingWorker<Void, ChatMessageWindow>() {

            @Override
            protected Void doInBackground() throws Exception {
                // Iterate over all panels
                for (ChatMessageWindow window : chatPanels.values()) {                    
                    if (window == activeWindow) {
                        continue;
                    }
                    
                    publish(window);
                    Thread.sleep(5); 
                }
                return null;
            }

            @Override
            protected void process(List<ChatMessageWindow> chunks) {
                for (ChatMessageWindow window : chunks) {
                    if (window.getChatMessagesPanel() != null) {
                        window.getChatMessagesPanel().initStyles();
                    }
                }
            }
        };

        // 4. Start the background task
        worker.execute();
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
        list = new com.generic.tools.components.list.UIList();
        leftPanel = new javax.swing.JPanel();
        inputPanel = new com.generic.tools.ai.components.ChatInputPanel();
        topPanel = new javax.swing.JPanel();
        topLeft = new javax.swing.JPanel();
        modelList = new javax.swing.JComboBox<>();
        knowledgeBaseBtn = new javax.swing.JButton();
        path = new javax.swing.JLabel();
        topRight = new javax.swing.JPanel();
        newChatBtn = new javax.swing.JButton();

        setLayout(new java.awt.BorderLayout());

        splitPane.setDividerSize(3);
        splitPane.setResizeWeight(1.0);
        splitPane.setOneTouchExpandable(true);

        rightPanel.setLayout(new javax.swing.BoxLayout(rightPanel, javax.swing.BoxLayout.PAGE_AXIS));
        rightPanel.add(list);

        splitPane.setRightComponent(rightPanel);

        leftPanel.setLayout(new java.awt.BorderLayout());
        leftPanel.add(inputPanel, java.awt.BorderLayout.PAGE_END);

        topPanel.setLayout(new javax.swing.BoxLayout(topPanel, javax.swing.BoxLayout.LINE_AXIS));

        topLeft.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        modelList.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "gpt-oss:20b-cloud", "qwen3-coder:480b-cloud", "llama3.2", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gpt-4o", "gpt-3.5-turbo" }));
        modelList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                modelListActionPerformed(evt);
            }
        });
        topLeft.add(modelList);

        knowledgeBaseBtn.setText("📂 Select Docs Folder");
        knowledgeBaseBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                knowledgeBaseBtnActionPerformed(evt);
            }
        });
        topLeft.add(knowledgeBaseBtn);

        path.setText("No Docs Selected");
        topLeft.add(path);

        topPanel.add(topLeft);

        topRight.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        newChatBtn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/chat/icons8-chat-20.png"))); // NOI18N
        newChatBtn.setText("New Chat");
        newChatBtn.setPreferredSize(new java.awt.Dimension(114, 25));
        newChatBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newChatBtnActionPerformed(evt);
            }
        });
        topRight.add(newChatBtn);

        topPanel.add(topRight);

        leftPanel.add(topPanel, java.awt.BorderLayout.PAGE_START);

        splitPane.setLeftComponent(leftPanel);

        add(splitPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void newChatBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newChatBtnActionPerformed
       startNewChat();
    }//GEN-LAST:event_newChatBtnActionPerformed

    private void modelListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modelListActionPerformed
       currentMessageWindow.setModel((String) modelList.getSelectedItem());
       currentMessageWindow.newAgent(); 
    }//GEN-LAST:event_modelListActionPerformed

    private void knowledgeBaseBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_knowledgeBaseBtnActionPerformed
        getKnowledgeBase();
    }//GEN-LAST:event_knowledgeBaseBtnActionPerformed

    public void getKnowledgeBase() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            knowledgeBase = selectedDir.getAbsolutePath();
            path.setText("Docs: " + selectedDir.getName()); // Show short name
            startNewChat();
        }

        
    }
      

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.generic.tools.ai.components.ChatInputPanel inputPanel;
    private javax.swing.JButton knowledgeBaseBtn;
    private javax.swing.JPanel leftPanel;
    private com.generic.tools.components.list.UIList list;
    private javax.swing.JComboBox<String> modelList;
    private javax.swing.JButton newChatBtn;
    private javax.swing.JLabel path;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JSplitPane splitPane;
    private javax.swing.JPanel topLeft;
    private javax.swing.JPanel topPanel;
    private javax.swing.JPanel topRight;
    // End of variables declaration//GEN-END:variables
}
