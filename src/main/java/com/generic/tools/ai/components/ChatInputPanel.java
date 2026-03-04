package com.generic.tools.ai.components;

import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.text.DefaultEditorKit;

/**
 *
 * @author vivek
 */
public class ChatInputPanel extends javax.swing.JPanel {

    private Consumer<String> onSend;
    private Runnable onStop; // 👈 1. Callback for Stop action
    private boolean isProcessing = false; // 👈 2. State tracking
    
    /**
     * Creates new form ChatMessagesPanel
     */
    public ChatInputPanel() {
        initComponents();
        initActions();
        initAutoResize();
    }
  
    private void initAutoResize() {
        // 1. Force text to wrap to the next line instead of scrolling sideways
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);

        // 2. Listen for text changes
        inputArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { adjustHeight(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { adjustHeight(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { adjustHeight(); }
        });
    }

    private void adjustHeight() {
        int minRows = 2;  // Starting height
        int maxRows = 30; // Max height before scrollbar kicks in

        java.awt.FontMetrics fm = inputArea.getFontMetrics(inputArea.getFont());
        int rowHeight = fm.getHeight();
        if (rowHeight == 0) return; // Failsafe

        // --- THE FIX ---
        // Temporarily set rows to 0 so getPreferredSize calculates based PURELY 
        // on the current text content, ignoring whatever size it grew to previously.
        int currentRows = inputArea.getRows();
        inputArea.setRows(0); 
        int preferredHeight = inputArea.getPreferredSize().height;
        inputArea.setRows(currentRows); // Restore immediately (causes no visual flicker)
        // ---------------
        
        java.awt.Insets insets = inputArea.getInsets();
        int innerHeight = preferredHeight - insets.top - insets.bottom;
        
        // Calculate visual lines (safeguard against 0)
        int currentVisualLines = Math.max(1, innerHeight / rowHeight);

        // Determine the new row count, bounded by min and max
        int targetRows = Math.max(minRows, Math.min(currentVisualLines, maxRows));

        // Apply changes if the row count needs to update
        if (inputArea.getRows() != targetRows) {
            inputArea.setRows(targetRows);
            
            // Revalidate the scroll pane and the panel to redraw the UI
            if (scrollPane != null) {
                scrollPane.revalidate();
            }
            revalidate();
            
            // Tell the parent window to adjust
            if (getParent() != null) {
                getParent().revalidate();
            }
        }
    }
    
    public void clearInput() {
        inputArea.setText("");        
    }
    
     private void initActions() {
        // 3. Smart Button Logic: Toggle between Send and Stop
        sendBtn.addActionListener(e -> {
            if (isProcessing) {
                fireStop();
            } else {
                fireSend();
            }
        });

        // ENTER = send (Only if NOT processing)
        inputArea.getInputMap().put(
                javax.swing.KeyStroke.getKeyStroke("ENTER"),
                "send"
        );
        inputArea.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!isProcessing) {
                    fireSend();
                }
            }
        });

        // SHIFT+ENTER = newline
        inputArea.getInputMap().put(
                javax.swing.KeyStroke.getKeyStroke("shift ENTER"),
                "shift-insert-break"
        );
        inputArea.getActionMap().put("shift-insert-break", new DefaultEditorKit.InsertBreakAction());
    }

    private void fireSend() {
        if (onSend == null) return;
        String text = inputArea.getText().trim();
        if (!text.isEmpty()) {
            // 4. Lock UI immediately
            setProcessing(true); 
            onSend.accept(text);
        }
    }

    private void fireStop() {
        // 5. Trigger external stop logic
        if (onStop != null) {
            onStop.run();
        }
        // Note: The controller should call setProcessing(false) after canceling.
    }
    
    // ===== Public API =====

    /**
     * Controls the UI state.
     * @param processing true = show "Stop", disable input; false = show "Send", enable input.
     */
    public void setProcessing(boolean processing) {
        this.isProcessing = processing;
        
        if (processing) {
            sendBtn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/chat/icons8-stop-24.png"))); // NOI18N
            sendBtn.setToolTipText("Stop");
            // Optional: Change color to red/orange to indicate stop action
            // sendBtn.setForeground(java.awt.Color.RED); 
            inputArea.setEnabled(false); // Lock input while thinking
        } else {            
            // sendBtn.setForeground(java.awt.Color.BLACK);
            sendBtn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/chat/icons8-sent-24.png"))); // NOI18N
            sendBtn.setToolTipText("Send Message");
            inputArea.setEnabled(true);
            inputArea.requestFocusInWindow();
        }
    }
    
    // 👈 6. Setter for Stop callback
    public void setOnStop(Runnable onStop) {
        this.onStop = onStop;
    }

    // ===== Public API =====
    public void setOnSend(Consumer<String> onSend) {
        this.onSend = onSend;
    }

    public void setEnabledInput(boolean enabled) {
        inputArea.setEnabled(enabled);
        sendBtn.setEnabled(enabled);
    }

    public void setPlaceholder(String text) {
        inputArea.setText(text);
    }

    public void focusInput() {
        inputArea.requestFocusInWindow();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        scrollPane = new javax.swing.JScrollPane();
        inputArea = new javax.swing.JTextArea();
        jPanel1 = new javax.swing.JPanel();
        sendBtn = new com.generic.tools.components.button.UIButton();

        setLayout(new java.awt.BorderLayout(0, 6));

        inputArea.setColumns(20);
        inputArea.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        inputArea.setRows(2);
        scrollPane.setViewportView(inputArea);

        add(scrollPane, java.awt.BorderLayout.CENTER);

        jPanel1.setAlignmentX(0.0F);
        jPanel1.setAlignmentY(0.0F);
        jPanel1.setPreferredSize(new java.awt.Dimension(50, 36));
        jPanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));

        sendBtn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/chat/icons8-sent-24.png"))); // NOI18N
        jPanel1.add(sendBtn);

        add(jPanel1, java.awt.BorderLayout.LINE_END);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextArea inputArea;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane scrollPane;
    private com.generic.tools.components.button.UIButton sendBtn;
    // End of variables declaration//GEN-END:variables
}
