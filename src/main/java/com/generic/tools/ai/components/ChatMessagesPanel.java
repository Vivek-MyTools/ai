package com.generic.tools.ai.components;

import com.generic.tools.ai.helper.ChatMessageData;
import com.generic.tools.lnf.theme.LnfTheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
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
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
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
            "(?s)```(\\w*)\\n?(.*?)```|" +                                         // 1-2: Code
            "(\\$\\$(?s:.*?)\\$\\$|\\\\\\((?s:.*?)\\\\\\)|\\\\\\[(?s:.*?)\\\\\\]|" +    // 3: Display Math
            "\\$(?!\\{)([^$]+?)\\$)|" +                                              // 4: Inline Math
            "<?(https?://[\\w\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=]+)>?|" +       // 5: Raw URL
            "\\[(.*?)\\]\\((https?://.*?)\\)|" +                                     // 6-7: Md Link
            // TABLE REGEX:
            "((?-s)(?:(?:^|\\n)[^\\n]*\\|[^\\n]*\\n)+(?:[^\\n]*\\|[-:| ]+\\|?[^\\n]*\\n)(?:[^\\n]*\\|[^\\n]*(?:\\n|$))*)" 
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
        // 1. DO NOT call this.removeAll(). It kills the ScrollPane.
        // Instead, clear the TextPane content.
        chatPane.setText(""); 
        
        // 2. Clear the internal history list so we don't duplicate data
        // (Because addAgentMessage/addUserMessage adds to the list again)
        this.messageHistory.clear();

        // 3. Re-create content
        for (ChatMessageData msg : history) {
            if ("USER".equals(msg.role)) {
                addUserMessage(msg.content);
            } else {
                addAgentMessage(msg.content);
            }
        }
        
        // 4. Scroll to bottom
        javax.swing.SwingUtilities.invokeLater(() -> {
             if (jScrollPane1.getVerticalScrollBar() != null) {
                 jScrollPane1.getVerticalScrollBar().setValue(
                     jScrollPane1.getVerticalScrollBar().getMaximum()
                 );
             }
        });
    }

    public void initStyles() {
        Point savedViewPos = null;
        if (jScrollPane1 != null && jScrollPane1.getViewport() != null) {
            savedViewPos = jScrollPane1.getViewport().getViewPosition();
        }
        
        Color bg = ChatThemeUtils.getCanvasBackground();
        Color fg = ChatThemeUtils.getCanvasForeground();

        chatPane.setBackground(bg);
        chatPane.setForeground(fg);

        StyledDocument doc = chatPane.getStyledDocument();
        String mainFontName = ChatThemeUtils.getUnicodeFontName();        
        int fontSize = 14;

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
        int anchorIndex = getAnchorIndex();
        refreshAllComponents();
        if (savedViewPos != null && jScrollPane1 != null) {
            jScrollPane1.getViewport().setViewPosition(savedViewPos);
        }

        // --- 3. SCHEDULE PRECISE SNAP (For font metric changes) ---
        if (anchorIndex >= 0) snapToPosition(anchorIndex);
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

        // FIX: Sanitize Tofu
        text = text.replace('\u2011', '-').replace('\u00A0', ' ');

        Matcher m = MASTER_PATTERN.matcher(text);
        int lastIndex = 0;

        while (m.find()) {
            String preText = text.substring(lastIndex, m.start());
            if (!preText.isEmpty()) appendMarkdownText(preText);

            if (m.group(1) != null || m.group(2) != null) {
                // Code
                String content = m.group(2).trim();
                append("\n", agentMsg);
                insertComponent(CodeBlockFactory.createCodeComponent(content, chatPane.getBackground(), chatPane.getForeground()));
                append("\n", agentMsg);
            } 
            else if (m.group(3) != null || m.group(4) != null) { 
                // Math
                String mathRaw = (m.group(3) != null) ? m.group(3) : m.group(4);
                String mathContent = MathRenderer.extractMathContent(mathRaw);
                
                // Pass current theme color to renderer
                Icon icon = MathRenderer.latexToIcon(mathRaw, mathContent); 
                
                // Store metadata via new insertIconDirectly signature
                if (icon != null) insertIconDirectly(icon, mathRaw, mathContent);
                else append(mathRaw, agentMsg);
            }
            else if (m.group(5) != null) {
                // Raw URL
                append(m.group(5), linkStyle);
            }
            else if (m.group(6) != null && m.group(7) != null) {
                // Markdown Link
                String title = m.group(6);
                String url = m.group(7);
                SimpleAttributeSet linkAttrs = new SimpleAttributeSet(linkStyle);
                linkAttrs.addAttribute("LinkUrl", url); 
                linkAttrs.addAttribute("IsLink", true);
                try { chatPane.getStyledDocument().insertString(chatPane.getDocument().getLength(), title, linkAttrs); } catch (BadLocationException e) {}
            }
            else if (m.group(8) != null) {
                // Table
                String tableRaw = m.group(8);
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

        if (lastIndex < text.length()) {
           appendMarkdownText(text.substring(lastIndex));
        }
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

        // 1. Fetch authoritative colors
        Color currentBg = ChatThemeUtils.getCanvasBackground();
        Color currentFg = ChatThemeUtils.getCanvasForeground();
        
        // 2. Apply to chatPane immediately
        chatPane.setBackground(currentBg);
        chatPane.setForeground(currentFg);

        boolean isDark = ChatThemeUtils.isDarkTheme(currentBg);

        for (int i = 0; i < root.getElementCount(); i++) {
            Element line = root.getElement(i);
            for (int j = 0; j < line.getElementCount(); j++) {
                Element leaf = line.getElement(j);
                AttributeSet attrs = leaf.getAttributes();
                Component comp = StyleConstants.getComponent(attrs);

                if (comp instanceof JPanel) {
                    JPanel panel = (JPanel) comp;

                    if (panel.getClientProperty("isCodeBlock") == null
                            && panel.getClientProperty("isTableBlock") == null
                            && panel.getClientProperty("isUserBubble") == null
                            && panel.getComponentCount() > 0
                            && panel.getComponent(0) instanceof javax.swing.JPanel) {

                        // We found a wrapper! Switch target to the inner child.
                        panel = (javax.swing.JPanel) panel.getComponent(0);
                    }

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
                    else if (panel.getClientProperty("isUserBubble") != null) {
                        MessageBubbleFactory.applyTheme(panel, currentBg);
                    }
                    else if (panel.getClientProperty("isTableBlock") != null) {
                        Color borderColor = ChatThemeUtils.getTableBorderColor(currentBg);
                        panel.setBackground(borderColor);
                        panel.setBorder(javax.swing.BorderFactory.createLineBorder(borderColor, 1));
                        
                        Color headerBg = ChatThemeUtils.getTableHeaderColor(currentBg);
                        Color cellBg   = ChatThemeUtils.getTableCellColor(currentBg);
                        Color textColor = ChatThemeUtils.getContrastingTextColor(cellBg);

                        for (Component cell : panel.getComponents()) {
                            if (cell instanceof JPanel) {
                                JPanel cellPanel = (JPanel) cell;
                                if (Boolean.TRUE.equals(cellPanel.getClientProperty("isTableHeader"))) {
                                    cellPanel.setBackground(headerBg);
                                } else {
                                    cellPanel.setBackground(cellBg);
                                }

                                if (cellPanel.getComponentCount() > 0) {
                                    Component contentComp = cellPanel.getComponent(0);
                                    if (contentComp instanceof JTextPane) {
                                        JTextPane textPane = (JTextPane) contentComp;
                                        textPane.setForeground(textColor);
                                        
                                        StyledDocument cellDoc = textPane.getStyledDocument();
                                        SimpleAttributeSet colorAttr = new SimpleAttributeSet();
                                        StyleConstants.setForeground(colorAttr, textColor);
                                        
                                        int len = cellDoc.getLength();
                                        int pos = 0;
                                        while (pos < len) {
                                            Element elem = cellDoc.getCharacterElement(pos);
                                            AttributeSet elemAttrs = elem.getAttributes();
                                            int end = elem.getEndOffset();
                                            
                                            if (elemAttrs.getAttribute("LatexRaw") != null) {
                                                // REFRESH TABLE ICONS
                                                String rawMath = (String) elemAttrs.getAttribute("LatexRaw");
                                                String contentMath = (String) elemAttrs.getAttribute("LatexContent");
                                                Icon newIcon = MathRenderer.latexToIcon(rawMath, contentMath, textColor);
                                                if (newIcon != null) {
                                                    SimpleAttributeSet newAttrs = new SimpleAttributeSet(elemAttrs);
                                                    StyleConstants.setIcon(newAttrs, newIcon);
                                                    cellDoc.setCharacterAttributes(pos, end - pos, newAttrs, false);
                                                }
                                            } else if (!StyleConstants.isUnderline(elemAttrs)) {
                                                cellDoc.setCharacterAttributes(pos, end - pos, colorAttr, false);
                                            }
                                            pos = end;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } 
                else if (attrs.getAttribute("LatexRaw") != null) {
                    // REFRESH MAIN CHAT ICONS
                    String rawMath = (String) attrs.getAttribute("LatexRaw");
                    String contentMath = (String) attrs.getAttribute("LatexContent");
                    
                    Icon newIcon = MathRenderer.latexToIcon(rawMath, contentMath, currentFg);
                    
                    if (newIcon != null) {
                        SimpleAttributeSet newAttrs = new SimpleAttributeSet(attrs);
                        StyleConstants.setIcon(newAttrs, newIcon);
                        doc.setCharacterAttributes(leaf.getStartOffset(), leaf.getEndOffset() - leaf.getStartOffset(), newAttrs, false);
                    }
                }
            }
        }
    }

    private void append(String text, Style style) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException ignored) {
        }
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
                    if (rect == null) {
                        return;
                    }
                    Dimension viewSize = jScrollPane1.getViewport().getViewSize();
                    Dimension extentSize = jScrollPane1.getViewport().getExtentSize();
                    int maxScrollY = viewSize.height - extentSize.height;
                    int finalY = Math.min(rect.y, Math.max(0, maxScrollY));
                    jScrollPane1.getViewport().setViewPosition(new Point(0, finalY));
                } catch (BadLocationException ignored) {
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        chatPane = new javax.swing.JTextPane(){
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true; 
            }
        };

        setLayout(new java.awt.BorderLayout());

        javax.swing.text.DefaultCaret caret = (javax.swing.text.DefaultCaret) chatPane.getCaret();
        caret.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        chatPane.setFont(new java.awt.Font("Dialog", 0, 14)); 
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