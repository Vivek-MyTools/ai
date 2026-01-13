package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 *
 * @author vivek
 */

public class ChatMessagesPanel extends javax.swing.JPanel {

    private int lastConversationIndex = 0;
    
    private static final Pattern MASTER_PATTERN = Pattern.compile(
            "(?s)```(\\w*)\\n?(.*?)```|" +                                      // Group 1 & 2: Code
            "(\\$\\$(?s:.*?)\\$\\$|\\\\\\((?s:.*?)\\\\\\)|\\\\\\[(?s:.*?)\\\\\\]|" + // Group 3: Display Math
            "\\$([^$]+?)\\$)"                                                    // Group 3: Inline Math (New)
    );
    
    private Icon userIcon;    

    private Style agentName, agentMsg, userName, userMsg;
    private Style codeStyle, keywordStyle, stringStyle, commentStyle, normalStyle;

    /**
     * Creates new form ChatMessagePanel
     */
    public ChatMessagesPanel() {
        initComponents();
        loadIcons();
        chatPane.setBackground(ChatThemeUtils.getCanvasBackground());
        chatPane.setForeground(ChatThemeUtils.getCanvasForeground());
        javax.swing.SwingUtilities.invokeLater(this::initStyles);
    }
    
    private void loadIcons() {
        // Try to load from resources, or create a simple fallback
        java.net.URL imgUrl = getClass().getResource("/chat/icons8-user-24.png");
        if (imgUrl != null) {
            userIcon = new ImageIcon(imgUrl);
        } else {
            // Fallback: A simple colored circle generated via code could go here
            // For now, null is fine (Factory handles null safely)
            userIcon = null; 
        }
    }
    
    public void initStyles() {
        int anchorIndex = getAnchorIndex(); // Save scroll position

        // 🎨 1. UPDATE GLOBAL COLORS (When theme changes)
        Color bg = ChatThemeUtils.getCanvasBackground();
        Color fg = ChatThemeUtils.getCanvasForeground();
        
        chatPane.setBackground(bg);
        chatPane.setForeground(fg);

        StyledDocument doc = chatPane.getStyledDocument();
        String mainFontName = ChatThemeUtils.getUnicodeFontName();
        int fontSize = 14;
       
        // --- DEFINE BASE STYLES ---
        agentName = chatPane.addStyle("AgentName", null);
        StyleConstants.setBold(agentName, true);
        StyleConstants.setFontFamily(agentName, mainFontName);
        StyleConstants.setFontSize(agentName, fontSize);
        agentName.removeAttribute(StyleConstants.Foreground); // Inherit from Pane

        agentMsg = chatPane.addStyle("AgentMsg", null);
        StyleConstants.setFontFamily(agentMsg, mainFontName);
        StyleConstants.setFontSize(agentMsg, fontSize);
        agentMsg.removeAttribute(StyleConstants.Foreground); // Inherit from Pane

        userName = chatPane.addStyle("UserName", null);
        StyleConstants.setBold(userName, true);
        StyleConstants.setFontFamily(userName, mainFontName);
        StyleConstants.setFontSize(userName, fontSize);
        StyleConstants.setForeground(userName, new java.awt.Color(70, 130, 180));

        userMsg = chatPane.addStyle("UserMsg", null);
        StyleConstants.setFontFamily(userMsg, mainFontName);
        StyleConstants.setFontSize(userMsg, fontSize);
        userMsg.removeAttribute(StyleConstants.Foreground);

        // --- 🔄 REFRESH ALL DYNAMIC COMPONENTS (Code & Bubbles) ---
        refreshAllComponents();

        if (anchorIndex >= 0) snapToPosition(anchorIndex);
    }
    
    // ===== Public API =====

    public void addUserMessage(String msg) {
        this.lastConversationIndex = chatPane.getDocument().getLength();
        
        // Use Factory, pass current background to calculate bubble color
        JPanel userBubble = MessageBubbleFactory.createUserBubble(
                msg, 
                chatPane.getBackground(), 
                userIcon
        );

        // Insert into document
        append("\n", userMsg); // Spacing before
        insertComponent(userBubble);
        append("\n", userMsg); // Spacing after
        
        snapToPosition(lastConversationIndex);
    }

    public void addAgentMessage(String msg) {
        append("Agent: ", agentName);
        parseAndAppend(msg);
        append("\n\n", agentMsg);
        snapToPosition(lastConversationIndex);
    }

    // ===== Internal Logic =====

    private void parseAndAppend(String text) {
        if (text == null || text.isEmpty()) return;

        Matcher m = MASTER_PATTERN.matcher(text);
        int lastIndex = 0;

        while (m.find()) {
            String preText = text.substring(lastIndex, m.start());
            if (!preText.isEmpty()) append(preText, agentMsg);

            if (m.group(1) != null || m.group(2) != null) {
                // --- Code Block ---
                String content = m.group(2).trim();
                append("\n", agentMsg);
                // Delegate to Factory
                insertComponent(CodeBlockFactory.createCodeComponent(
                        content, 
                        chatPane.getBackground(), 
                        chatPane.getForeground()
                ));
                append("\n", agentMsg);
            } else {
                // --- Math Block ---
                String mathRaw = m.group(3); 
                String mathContent = MathRenderer.extractMathContent(mathRaw);
                
                // Delegate to Renderer
                Icon icon = MathRenderer.latexToIcon(mathRaw, mathContent); 
                
                if (icon != null) {
                    insertIconDirectly(icon);
                } else {
                    append(mathRaw, agentMsg); // Fallback to raw text
                }
            }
            lastIndex = m.end();
        }

        if (lastIndex < text.length()) {
            append(text.substring(lastIndex), agentMsg);
        }
    }

    /**
     * Iterates through document to find existing Panels and re-applies theme.
     * Handles both Code Blocks and User Bubbles.
     */
    private void refreshAllComponents() {
        StyledDocument doc = chatPane.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        
        Color currentBg = chatPane.getBackground();
        Color currentFg = chatPane.getForeground();
        boolean isDark = ChatThemeUtils.isDarkTheme(currentBg);

        for (int i = 0; i < root.getElementCount(); i++) {
            Element line = root.getElement(i);
            for (int j = 0; j < line.getElementCount(); j++) {
                Element leaf = line.getElement(j);
                AttributeSet attrs = leaf.getAttributes();
                Component comp = StyleConstants.getComponent(attrs);
                
                if (comp instanceof JPanel) {
                    JPanel panel = (JPanel) comp;

                    // CASE 1: IT IS A CODE BLOCK
                    if (panel.getClientProperty("isCodeBlock") != null) {
                        JTextPane innerPane = (JTextPane) panel.getComponent(0);
                        String rawCode = (String) panel.getClientProperty("rawCode");
                        
                        Color codeBg = ChatThemeUtils.getCodeBackgroundColor(currentBg);
                        Color borderCol = isDark ? codeBg.brighter() : codeBg.darker();
                        
                        panel.setBackground(codeBg);
                        panel.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                             javax.swing.BorderFactory.createMatteBorder(1, 0, 1, 0, borderCol),
                             javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)
                        ));
                        
                        innerPane.setBackground(codeBg);
                        innerPane.setForeground(currentFg);
                        CodeBlockFactory.highlightCode(innerPane, rawCode, isDark);
                    }
                    
                    // CASE 2: IT IS A USER BUBBLE (NEW)
                    else if (panel.getClientProperty("isUserBubble") != null) {
                        MessageBubbleFactory.applyTheme(panel, currentBg);
                    }
                }
            }
        }
    }

    // ===== Helpers for Document Manipulation =====

    private void append(String text, Style style) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException ignored) {}
    }

    private void insertComponent(Component comp) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setComponent(attrs, comp);
            doc.insertString(doc.getLength(), " ", attrs);
        } catch (BadLocationException ignored) {}
    }

    private void insertIconDirectly(Icon icon) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            doc.insertString(doc.getLength(), " ", attrs);
        } catch (BadLocationException ignored) {}
    }

    // ===== Scroll Helpers =====

    private int getAnchorIndex() {
        if (jScrollPane1 != null && jScrollPane1.getViewport() != null) {
            Point currentPoint = jScrollPane1.getViewport().getViewPosition();
            return chatPane.viewToModel(currentPoint);
        }
        return 0;
    }

    private void snapToPosition(int index) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    Rectangle rect = chatPane.modelToView(index);
                    if (rect == null) return;
                    Dimension viewSize = jScrollPane1.getViewport().getViewSize();
                    Dimension extentSize = jScrollPane1.getViewport().getExtentSize();
                    int maxScrollY = viewSize.height - extentSize.height;
                    int finalY = Math.min(rect.y, Math.max(0, maxScrollY));
                    jScrollPane1.getViewport().setViewPosition(new Point(0, finalY));
                } catch (BadLocationException ignored) {}
            });
        });
    }
   
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        chatPane = new javax.swing.JTextPane(){
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true; // Force Text Wrap
            }
        };

        setLayout(new java.awt.BorderLayout());

        javax.swing.text.DefaultCaret caret = (javax.swing.text.DefaultCaret) chatPane.getCaret();
        caret.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        chatPane.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        chatPane.setText("");
        jScrollPane1.setViewportView(chatPane);
        chatPane.setEditable(false);

        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(jScrollPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextPane chatPane;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
}
