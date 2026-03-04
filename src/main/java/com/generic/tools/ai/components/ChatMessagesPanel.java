package com.generic.tools.ai.components;

import com.generic.tools.ai.helper.ChatMessageData;
import com.generic.tools.lnf.theme.LnfTheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

/**
 *
 * @author vivek
 */
public class ChatMessagesPanel extends javax.swing.JPanel {

    private javax.swing.Timer themeTimer;
    private int lastConversationIndex = 0;

   // --- MASTER REGEX PATTERN ---
    private static final Pattern MASTER_PATTERN = Pattern.compile(
            "(?s)```(\\w*)\\n?(.*?)```|" +                                              // 1-2: Code
            "(\\$\\$(?s:.*?)\\$\\$|\\\\\\((?s:.*?)\\\\\\)|\\\\\\[(?s:.*?)\\\\\\]|" +    // 3: Display Math
            "\\$(?!\\{)([^$]+?)\\$)|" +                                                 // 4: Inline Math
            // 5: Video (More robust: allow params like ?v=1 at end, though mainly extensions)
            "((?:https?://|file:/|/|[a-zA-Z]:\\\\)[^\\s<>]+\\.(?:mp4|webm|mkv))|" + 
            "<?(https?://[\\w\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=]+)>?|" +          // 6: Raw URL
            "\\[(.*?)\\]\\((https?://.*?)\\)|" +                                        // 7-8: Md Link
            "((?-s)(?:(?:^|\\n)[^\\n]*\\|[^\\n]*\\n)+(?:[^\\n]*\\|[-:| ]+\\|?[^\\n]*\\n)(?:[^\\n]*\\|[^\\n]*(?:\\n|$))*)" // 9: Table
    );
    
    private static final Pattern TEXT_FORMAT_PATTERN = Pattern.compile(
            "(\\*\\*(.+?)\\*\\*)|" +  // Group 1 & 2: **Bold**
            "(\\*(.+?)\\*)"           // Group 3 & 4: *Italic*
    );
    private Icon userIcon;

    private Style agentName, agentMsg, userName, userMsg;
    private Style agentMsgBold, agentMsgItalic; 
    private Style linkStyle;
    
    private List<ChatMessageData> messageHistory = new ArrayList<>();

    public ChatMessagesPanel() {
        initComponents();
        loadIcons();
        chatPane.setBackground(ChatThemeUtils.getCanvasBackground());
        chatPane.setForeground(ChatThemeUtils.getCanvasForeground());

        setupLinkListener();
        initStyles();
        setupExportMenu();
        LnfTheme.getInstance().addComponent(this);
    }
    
    private void setupExportMenu() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem exportItem = new JMenuItem("Export Chat to PDF");
        LnfTheme.getInstance().addComponent(popup);
        exportItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save PDF");
            fileChooser.setSelectedFile(new File("ChatExport.pdf"));
            
            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".pdf")) {
                    file = new File(file.getAbsolutePath() + ".pdf");
                }
                ChatExportUtils.exportToPdf(chatPane, file);
            }
        });

        popup.add(exportItem);

        chatPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { showPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { showPopup(e); }
            
            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    private void setupLinkListener() {
        MouseAdapter linkAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    Element elem = getElementAt(e.getPoint());
                    if (elem != null) {
                        AttributeSet attrs = elem.getAttributes();
                        if (Boolean.TRUE.equals(attrs.getAttribute("IsLink"))) {
                            String url = (String) attrs.getAttribute("LinkUrl");
                            if (url == null) {
                                try {
                                    int start = elem.getStartOffset();
                                    int end = elem.getEndOffset();
                                    url = chatPane.getDocument().getText(start, end - start);
                                } catch (BadLocationException ex) {}
                            }
                            if (url != null) ChatThemeUtils.openBrowser(url);
                        }
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Element elem = getElementAt(e.getPoint());
                if (elem != null) {
                    AttributeSet attrs = elem.getAttributes();
                    if (Boolean.TRUE.equals(attrs.getAttribute("IsLink"))) {
                        chatPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        return;
                    }
                }
                chatPane.setCursor(Cursor.getDefaultCursor());
            }
        };
        chatPane.addMouseListener(linkAdapter);
        chatPane.addMouseMotionListener(linkAdapter);
    }
    
    private Element getElementAt(Point p) {
        int pos = chatPane.viewToModel(p);
        if (pos >= 0) {
            StyledDocument doc = chatPane.getStyledDocument();
            return doc.getCharacterElement(pos);
        }
        return null;
    }
    
    public void clearChat() {
        chatPane.setText("");
        messageHistory.clear();
        lastConversationIndex = 0;
        
        initStyles();
    }
    
    private void loadIcons() {
        java.net.URL imgUrl = getClass().getResource("/chat/icons8-user-24.png");
        if (imgUrl != null) {
            userIcon = new ImageIcon(imgUrl);
        } else {
            userIcon = null;
        }
    }
    
    public List<ChatMessageData> getMessageHistory() {
        return messageHistory;
    }

    public void loadHistory(List<ChatMessageData> history) {
        chatPane.setText("");
        this.messageHistory.clear();

        for (ChatMessageData msg : history) {
            if ("USER".equals(msg.role)) {
                addUserMessage(msg.content);
            } else {
                addAgentMessage(msg.content);
            }
        }
                
        // Force scroll to bottom after loading history
        javax.swing.SwingUtilities.invokeLater(() -> {
             if (scrollPane != null && scrollPane.getVerticalScrollBar() != null) {
                 scrollPane.getVerticalScrollBar().setValue(
                      scrollPane.getVerticalScrollBar().getMaximum()
                 );
             }
        });
    }
  
    public void initStyles() {
        // 1. Capture the anchor BEFORE we change any colors/fonts
        int anchorIndex = getAnchorIndex();
        
        Color bg = ChatThemeUtils.getCanvasBackground();
        Color fg = ChatThemeUtils.getCanvasForeground();

        chatPane.setBackground(bg);
        chatPane.setForeground(fg);
        
        int fontSize = 14;
        StyledDocument doc = chatPane.getStyledDocument();
        String mainFontName = ChatThemeUtils.getUnicodeFontName();        
        chatPane.setFont(new Font(mainFontName, Font.PLAIN, fontSize));
        

        // ... (Keep your existing Style definitions here: agentName, agentMsg, etc.) ...
        agentName = chatPane.addStyle("AgentName", null);
        StyleConstants.setBold(agentName, true);
        StyleConstants.setFontFamily(agentName, mainFontName);
        StyleConstants.setFontSize(agentName, fontSize);
        agentName.removeAttribute(StyleConstants.Foreground);

        agentMsg = chatPane.addStyle("AgentMsg", null);
        StyleConstants.setFontFamily(agentMsg, mainFontName);
        StyleConstants.setFontSize(agentMsg, fontSize);
        agentMsg.removeAttribute(StyleConstants.Foreground);
        
        agentMsgBold = chatPane.addStyle("AgentMsgBold", agentMsg);
        StyleConstants.setBold(agentMsgBold, true);

        agentMsgItalic = chatPane.addStyle("AgentMsgItalic", agentMsg);
        StyleConstants.setItalic(agentMsgItalic, true);

        userName = chatPane.addStyle("UserName", null);
        StyleConstants.setBold(userName, true);
        StyleConstants.setFontFamily(userName, mainFontName);
        StyleConstants.setFontSize(userName, fontSize);
        StyleConstants.setForeground(userName, new java.awt.Color(70, 130, 180));

        userMsg = chatPane.addStyle("UserMsg", null);
        StyleConstants.setFontFamily(userMsg, mainFontName);
        StyleConstants.setFontSize(userMsg, fontSize);
        userMsg.removeAttribute(StyleConstants.Foreground);

        linkStyle = chatPane.addStyle("Link", null);
        StyleConstants.setForeground(linkStyle, new Color(0, 102, 204));
        StyleConstants.setUnderline(linkStyle, true);
        StyleConstants.setFontFamily(linkStyle, mainFontName);
        StyleConstants.setFontSize(linkStyle, fontSize);
        linkStyle.addAttribute("IsLink", true);
        
        // 2. Refresh visual components (Tables, Bubbles)
        refreshAllComponents();        
      
        // 3. Schedule the snap logic
        snapToPosition(anchorIndex);
    }

    public void addUserMessage(String msg) {
        messageHistory.add(new ChatMessageData("USER", msg));
        this.lastConversationIndex = chatPane.getDocument().getLength();
        JPanel userBubble = MessageBubbleFactory.createBubble(
                msg,
                chatPane.getBackground(),
                userIcon,
                true 
        );
        append("\n", userMsg);
        insertComponent(userBubble);
        append("\n", userMsg);
        snapToPosition(lastConversationIndex);
    }

    public void addAgentMessage(String msg) {
        messageHistory.add(new ChatMessageData("AGENT", msg));
        append("\nAgent: ", agentName);
        parseAndAppend(msg);
        append("\n\n", agentMsg);
        snapToPosition(lastConversationIndex);
    }

    // ===== Internal Logic =====
   private void parseAndAppend(String text) {
        if (text == null || text.isEmpty()) return;
        
        // Sanitize Tofu
        text = text.replace('\u2011', '-').replace('\u00A0', ' ');
        
        Matcher m = MASTER_PATTERN.matcher(text);
        int lastIndex = 0;
        
        while (m.find()) {
            String preText = text.substring(lastIndex, m.start());
            if (!preText.isEmpty()) appendMarkdownText(preText);

            // Group 1-2: Code Block
            if (m.group(1) != null || m.group(2) != null) { 
                String content = m.group(2).trim();
                append("\n", agentMsg);
                insertComponent(CodeBlockFactory.createCodeComponent(content, chatPane.getBackground(), chatPane.getForeground()));
                append("\n", agentMsg);
            } 
            // Group 3-4: Math
            else if (m.group(3) != null || m.group(4) != null) { 
                String mathRaw = (m.group(3) != null) ? m.group(3) : m.group(4);
                String mathContent = MathRenderer.extractMathContent(mathRaw);
                Icon icon = MathRenderer.latexToIcon(mathRaw, mathContent); 
                if (icon != null) insertIconDirectly(icon, mathRaw, mathContent);
                else append(mathRaw, agentMsg);
            }
            // Group 5: Video Files
            else if (m.group(5) != null) {
                String videoPath = m.group(5);
                append("\n", agentMsg);
                // Create the video component (Pure Swing version we built earlier)
                insertComponent(VideoBlockFactory.createVideoComponent(videoPath));
                append("\n", agentMsg);
            }
            // Group 6: Raw URLs (Generic)
            else if (m.group(6) != null) { 
                append(m.group(6), linkStyle);
            }
            // Group 7-8: Markdown Links
            else if (m.group(7) != null && m.group(8) != null) { 
                String title = m.group(7);
                String url = m.group(8);
                // Edge Case: If the markdown link URL is actually a video, override it!
                if (url.endsWith(".mp4") || url.endsWith(".webm") || url.endsWith(".mkv")) {
                    append("\n", agentMsg);
                    insertComponent(VideoBlockFactory.createVideoComponent(url));
                    append("\n", agentMsg);
                } else {
                    SimpleAttributeSet linkAttrs = new SimpleAttributeSet(linkStyle);
                    linkAttrs.addAttribute("LinkUrl", url); 
                    linkAttrs.addAttribute("IsLink", true);
                    try { chatPane.getStyledDocument().insertString(chatPane.getDocument().getLength(), title, linkAttrs); } catch (BadLocationException e) {}
                }
            }
            // Group 9: Tables
            else if (m.group(9) != null) { 
                String tableRaw = m.group(9);
                tableRaw = MathRenderer.fixMathForTable(tableRaw);
                append("\n", agentMsg);
                Component tableComp = TableBlockFactory.createTable(tableRaw, chatPane.getBackground());
                JPanel wrapper = new JPanel(new java.awt.BorderLayout());
                wrapper.setOpaque(false);
                wrapper.add(tableComp, java.awt.BorderLayout.CENTER);                
                insertComponent(wrapper);
                append("\n", agentMsg);
            }
            
            lastIndex = m.end();
        }
        
        if (lastIndex < text.length()) appendMarkdownText(text.substring(lastIndex));
    }
    
    private void appendMarkdownText(String text) {
        if (text == null || text.isEmpty()) return;

        Matcher m = TEXT_FORMAT_PATTERN.matcher(text);
        int lastIndex = 0;

        while (m.find()) {
            String plain = text.substring(lastIndex, m.start());
            if (!plain.isEmpty()) append(plain, agentMsg);

            if (m.group(1) != null) { 
                String content = m.group(2);
                append(content, agentMsgBold);
            } else if (m.group(3) != null) {
                String content = m.group(4); 
                append(content, agentMsgItalic);
            }

            lastIndex = m.end();
        }

        if (lastIndex < text.length()) {
            append(text.substring(lastIndex), agentMsg);
        }
    }
    
   private void refreshAllComponents() {
        StyledDocument doc = chatPane.getStyledDocument();
        Element root = doc.getDefaultRootElement();

        // 1. Set Global Colors Immediately (Instant visual feedback)
        Color currentBg = ChatThemeUtils.getCanvasBackground();
        Color currentFg = ChatThemeUtils.getCanvasForeground();
        
        chatPane.setBackground(currentBg);
        chatPane.setForeground(currentFg);
        
        // 2. Stop any previous update task if it's still running
        if (themeTimer != null && themeTimer.isRunning()) {
            themeTimer.stop();
        }

        // 3. CHUNKED UPDATE STRATEGY (Non-blocking)
        // Process 100 elements, then yield to UI thread, then repeat.
        final int CHUNK_SIZE = 100; 
        final int totalElements = root.getElementCount();
        
        themeTimer = new javax.swing.Timer(1, new java.awt.event.ActionListener() {
            int currentIndex = 0;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // Calculate the end index for this batch
                int limit = Math.min(currentIndex + CHUNK_SIZE, totalElements);

                // Process only this batch
                for (int i = currentIndex; i < limit; i++) {
                    Element line = root.getElement(i);
                    for (int j = 0; j < line.getElementCount(); j++) {
                        Element leaf = line.getElement(j);
                        AttributeSet attrs = leaf.getAttributes();
                        Component comp = StyleConstants.getComponent(attrs);

                        // Process JComponents (Bubbles, Code Blocks, Tables)
                        if (comp instanceof JComponent) {
                            processComponentBlock((JComponent) comp, currentBg, currentFg);
                        }
                    }
                }
                
                // Move cursor forward
                currentIndex = limit;

                // Stop if we reached the end
                if (currentIndex >= totalElements) {
                    ((javax.swing.Timer)e.getSource()).stop();
                }
            }
        });
        
        // Start immediately
        themeTimer.setInitialDelay(0);
        themeTimer.start();
    }

    private void processComponentBlock(JComponent panel, Color currentBg, Color currentFg) {
        // Unwrap wrappers (e.g., Table wrapper or simple containers)
        if (panel.getClientProperty("isCodeBlock") == null
                && panel.getClientProperty("isTableBlock") == null
                && panel.getClientProperty("isUserBubble") == null
                && panel.getComponentCount() > 0
                && panel.getComponent(0) instanceof JComponent) {
            panel = (JComponent) panel.getComponent(0);
        }

        // --- 1. CODE BLOCKS ---
        if (panel.getClientProperty("isCodeBlock") != null) {
            // Force refresh colors on Code Blocks
            CodeBlockFactory.refreshCodeBlock((JPanel) panel, currentBg, currentFg);
        } 
        // --- 2. USER BUBBLES ---
        else if (panel.getClientProperty("isUserBubble") != null && panel instanceof JPanel) {
            MessageBubbleFactory.applyTheme((JPanel) panel, currentBg);
        } 
        // --- 3. TABLES ---
        else {
            // Check for Table (might be JPanel or JScrollPane)
            JPanel gridPanel = null;
            if (Boolean.TRUE.equals(panel.getClientProperty("isTableBlock")) && panel instanceof JPanel) {
                gridPanel = (JPanel) panel;
            } else if (panel instanceof JScrollPane) {
                Component view = ((JScrollPane) panel).getViewport().getView();
                if (view instanceof JPanel) gridPanel = (JPanel) view;
            }

            if (gridPanel != null) {
                refreshTableBlock(gridPanel, currentBg);
            }
        }
    }

    private void refreshTableBlock(JPanel gridPanel, Color currentBg) {
        Color borderColor = ChatThemeUtils.getTableBorderColor(currentBg);
        gridPanel.setBackground(borderColor);
        gridPanel.setBorder(javax.swing.BorderFactory.createLineBorder(borderColor, 1));
        
        Color headerBg = ChatThemeUtils.getTableHeaderColor(currentBg);
        Color cellBg   = ChatThemeUtils.getTableCellColor(currentBg);
        Color textColor = ChatThemeUtils.getContrastingTextColor(cellBg);

        for (Component cell : gridPanel.getComponents()) {
            if (cell instanceof JPanel) {
                JPanel cellPanel = (JPanel) cell;
                boolean isHeader = Boolean.TRUE.equals(cellPanel.getClientProperty("isTableHeader"));
                cellPanel.setBackground(isHeader ? headerBg : cellBg);

                if (cellPanel.getComponentCount() > 0 && cellPanel.getComponent(0) instanceof JTextPane) {
                    JTextPane textPane = (JTextPane) cellPanel.getComponent(0);
                    updateTableCellColor(textPane, textColor);                    
                }
            }
        }
    }
    
    private void updateTableCellColor(JTextPane textPane, Color newColor) {
        // 1. Update component property
        textPane.setForeground(newColor);
        
        // 2. Update Attributes directly
        StyledDocument doc = textPane.getStyledDocument();
        SimpleAttributeSet colorAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(colorAttr, newColor);
        
        // We iterate through the text to skip Links (which are usually Blue/Underlined)
        int len = doc.getLength();
        int pos = 0;
        
        while (pos < len) {
            Element elem = doc.getCharacterElement(pos);
            AttributeSet attrs = elem.getAttributes();
            int end = elem.getEndOffset();
            
            // Check if it's a link (Links have underline in TableCellRenderer)
            boolean isLink = StyleConstants.isUnderline(attrs);
            
            if (!isLink) {
                // Apply new color, "false" means merge (don't remove Bold/Italic)
                doc.setCharacterAttributes(pos, end - pos, colorAttr, false);
            }
            
            pos = end;
        }
    }
    
    private void append(String text, Style style) {
        try {
            ChatThemeUtils.smartInsert(chatPane.getStyledDocument(), text, style);
        } catch (BadLocationException ignored) {}
    }
    
    private void insertComponent(Component comp) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setComponent(attrs, comp);
            doc.insertString(doc.getLength(), " ", attrs);
        } catch (BadLocationException ignored) {
        }
    }

    // New signature storing Metadata
    private void insertIconDirectly(Icon icon, String raw, String content) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            attrs.addAttribute("LatexRaw", raw);
            attrs.addAttribute("LatexContent", content);
            doc.insertString(doc.getLength(), " ", attrs);
        } catch (BadLocationException ignored) {
        }
    }

    private int getAnchorIndex() {
        if (scrollPane == null || scrollPane.getViewport() == null) {
            return -1;
        }
        if (messageHistory.isEmpty()) {
            return 0;
        }

        javax.swing.JScrollBar vsb = scrollPane.getVerticalScrollBar();
        if (vsb == null) {
            return -1;
        }

        int value = vsb.getValue();
        int extent = vsb.getVisibleAmount();
        int max = vsb.getMaximum();

        // SAFETY: If scrollbar claims 0 height, the layout is invalid/reset.
        // Assume we were at the bottom (Standard Chat Behavior).
        if (max == 0 || extent == 0) {
            return -1;
        }

        // STICK-TO-BOTTOM: If we are near the bottom (100px threshold), stay there.
        if (value + extent >= max - 100) {
            return -1;
        }

        Point p = scrollPane.getViewport().getViewPosition();
        return chatPane.viewToModel(p);
    }
   
    private void snapToPosition(int index) {
        // Use nested invokeLater (Double Barrier)
        // This waits for the text insertion AND the complex component layout (Code Blocks) 
        // to finish before we attempt to scroll.
        SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(() -> {
                if (scrollPane == null || scrollPane.getViewport() == null) return;

                // Force the ScrollPane to recognize the new heights immediately
                scrollPane.validate(); 

                try {
                    javax.swing.JScrollBar vsb = scrollPane.getVerticalScrollBar();
                    if (vsb == null) return;

                    // CASE 1: Stick to Bottom (Only if explicitly requested)
                    if (index == -1) {
                         vsb.setValue(vsb.getMaximum());
                    } 
                    // CASE 2: Restore Reading Position (User Message)
                    else {
                        Rectangle rect = chatPane.modelToView(index);
                        if (rect != null) {
                            Dimension viewSize = scrollPane.getViewport().getViewSize();
                            Dimension extentSize = scrollPane.getViewport().getExtentSize();
                            int maxScrollY = viewSize.height - extentSize.height;
                            
                            // Align the User Message to the TOP of the view
                            int finalY = rect.y;
                            
                            // Ensure we don't scroll past valid bounds
                            finalY = Math.min(finalY, Math.max(0, maxScrollY));
                            
                            scrollPane.getViewport().setViewPosition(new Point(0, finalY));
                        }
                    }
                } catch (Exception ignored) { }
            });
        });
    }
    
   
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        scrollPane = new javax.swing.JScrollPane();
        chatPane = new javax.swing.JTextPane(){
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true; // Force Text Wrap
            }
        };

        setLayout(new java.awt.BorderLayout());

        javax.swing.text.DefaultCaret caret = (javax.swing.text.DefaultCaret) chatPane.getCaret();
        caret.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        scrollPane.setViewportView(chatPane);
        chatPane.setEditable(false);

        scrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextPane chatPane;
    private javax.swing.JScrollPane scrollPane;
    // End of variables declaration//GEN-END:variables
}