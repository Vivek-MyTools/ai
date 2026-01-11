package com.generic.tools.ai.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.scilab.forge.jlatexmath.TeXFormula;

/**
 *
 * @author vivek
 */
public class ChatMessagesPanel extends javax.swing.JPanel {

    private int lastConversationIndex = 0;
    // Regex constants (Same as before)
    private static final Pattern MASTER_PATTERN = Pattern.compile(
            "(?s)```(\\w*)\\n?(.*?)```|(\\$\\$(?s:.*?)\\$\\$|\\\\\\((?s:.*?)\\\\\\)|\\\\\\[(?s:.*?)\\\\\\])"
    );
    private static final Pattern SYNTAX_PATTERN = Pattern.compile(
            "(?<COMMENT>//.*|/\\*(?s:.*?)\\*/)|(?<STRING>\"([^\"\\\\]|\\\\.)*\")|(?<KEYWORD>\\b(public|private|protected|class|static|final|void|int|double|new|return|if|else|for|while|import)\\b)"
    );

    private Style agentName, agentMsg, userName, userMsg;
    private Style codeStyle, keywordStyle, stringStyle, commentStyle, normalStyle;

    /**
     * Creates new form ChatMessagePanel
     */
    public ChatMessagesPanel() {
        initComponents();
        javax.swing.SwingUtilities.invokeLater(this::initStyles);
    }

    private String getUnicodeFontName() {
        String[] fontNames = java.awt.GraphicsEnvironment
                                .getLocalGraphicsEnvironment()
                                .getAvailableFontFamilyNames();
        
        for (String fontName : fontNames) {
            if (fontName.equalsIgnoreCase("Nirmala UI")) {
                return "Nirmala UI"; // ✅ Best for Hindi, Telugu, Tamil, etc.
            }
        }
        
        // Fallbacks
        for (String fontName : fontNames) {
            if (fontName.equalsIgnoreCase("Arial Unicode MS")) return "Arial Unicode MS";
        }
        
        // 'Dialog' is Java's composite font that attempts to link system fonts
        return "Dialog"; 
    }

    private void initStyles() {
        StyledDocument doc = chatPane.getStyledDocument();
        
        // 1. Detect the best font for languages
        String mainFontName = getUnicodeFontName(); 
        int fontSize = 14;

        // --- CHAT STYLES (Use Language Font) ---
        
        agentName = chatPane.addStyle("AgentName", null);
        StyleConstants.setBold(agentName, true);
        StyleConstants.setFontFamily(agentName, mainFontName); // 👈 Critical Fix
        StyleConstants.setFontSize(agentName, fontSize);
        agentName.removeAttribute(StyleConstants.Foreground);

        agentMsg = chatPane.addStyle("AgentMsg", null);
        StyleConstants.setFontFamily(agentMsg, mainFontName);  // 👈 Critical Fix
        StyleConstants.setFontSize(agentMsg, fontSize);
        agentMsg.removeAttribute(StyleConstants.Foreground);

        userName = chatPane.addStyle("UserName", null);
        StyleConstants.setBold(userName, true);
        StyleConstants.setFontFamily(userName, mainFontName);  // 👈 Critical Fix
        StyleConstants.setFontSize(userName, fontSize);
        StyleConstants.setForeground(userName, new java.awt.Color(70, 130, 180));

        userMsg = chatPane.addStyle("UserMsg", null);
        StyleConstants.setFontFamily(userMsg, mainFontName);   // 👈 Critical Fix
        StyleConstants.setFontSize(userMsg, fontSize);
        userMsg.removeAttribute(StyleConstants.Foreground);

        // --- CODE STYLES (Keep Consolas for alignment) ---
        
        codeStyle = chatPane.addStyle("CodeBase", null);
        StyleConstants.setFontFamily(codeStyle, "Consolas"); 
        StyleConstants.setFontSize(codeStyle, 13);
        
        // Light Card Look
        StyleConstants.setBackground(codeStyle, new java.awt.Color(245, 245, 245));
        StyleConstants.setForeground(codeStyle, Color.BLACK);

        keywordStyle = chatPane.addStyle("Keyword", codeStyle);
        StyleConstants.setForeground(keywordStyle, new Color(0, 0, 180)); 
        StyleConstants.setBold(keywordStyle, true);

        stringStyle = chatPane.addStyle("String", codeStyle);
        StyleConstants.setForeground(stringStyle, new Color(0, 100, 0));

        commentStyle = chatPane.addStyle("Comment", codeStyle);
        StyleConstants.setForeground(commentStyle, new Color(100, 100, 100));
        StyleConstants.setItalic(commentStyle, true);

        normalStyle = chatPane.addStyle("NormalCode", codeStyle);
        StyleConstants.setForeground(normalStyle, Color.BLACK);
    }
    
    // ===== Public API =====
    public void addUserMessage(String msg) {
        this.lastConversationIndex = chatPane.getDocument().getLength();
        append("You: ", userName);
        append(msg + "\n\n", userMsg);
       
        snapToPosition(lastConversationIndex);
    }

    public void addAgentMessage(String msg) {
        append("Agent: ", agentName);
        parseAndAppend(msg);
        append("\n\n", agentMsg);
        snapToPosition(lastConversationIndex);
    }

   private java.awt.Component createCodeComponent(String codeContent) {
        // A. The Inner Code Editor
        JTextPane codePane = new JTextPane();
        codePane.setEditable(false);
        codePane.setFont(new java.awt.Font("Consolas", 0, 13));
        
        // 🎨 "Light Card" Theme (Fixed colors for visibility)
        Color cardBg = new Color(245, 245, 245); // Light Gray
        codePane.setBackground(cardBg);
        codePane.setForeground(Color.BLACK);
        
        // B. Apply Syntax Highlighting to THIS component
        highlightCodeInComponent(codePane, codeContent);

        // C. The Wrapper Panel (Adds Border & Margins)
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(cardBg); // Match background
        wrapper.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(200, 200, 200)), // Top/Bottom Line
            BorderFactory.createEmptyBorder(10, 10, 10, 10) // Internal Padding
        ));
        wrapper.add(codePane, BorderLayout.CENTER);
        
        // D. Prevent it from growing wider than the chat window (optional fix)
        wrapper.setSize(new java.awt.Dimension(400, wrapper.getPreferredSize().height));

        return wrapper;
    }

    /**
     * 2. 🖌️ LOCAL HIGHLIGHTER: Runs strictly on the code component
     */
    private void highlightCodeInComponent(JTextPane pane, String code) {
        StyledDocument doc = pane.getStyledDocument();
        
        // Define Local Styles (Fixed Light Theme)
        Style def = pane.addStyle("Default", null);
        StyleConstants.setForeground(def, Color.BLACK);

        Style key = pane.addStyle("Keyword", null);
        StyleConstants.setForeground(key, new Color(0, 0, 180)); // Blue
        StyleConstants.setBold(key, true);

        Style str = pane.addStyle("String", null);
        StyleConstants.setForeground(str, new Color(0, 100, 0)); // Green

        Style cmt = pane.addStyle("Comment", null);
        StyleConstants.setForeground(cmt, new Color(100, 100, 100)); // Gray
        StyleConstants.setItalic(cmt, true);

        try {
            // Insert raw text first
            doc.insertString(0, code, def);

            // Parse and Colorize
            // (Using the same regex pattern from before)
            Pattern syntaxPattern = Pattern.compile(
                "(?<COMMENT>//.*|/\\*(?s:.*?)\\*/)|(?<STRING>\"([^\"\\\\]|\\\\.)*\")|(?<KEYWORD>\\b(public|private|protected|class|static|final|void|int|double|new|return|if|else|for|while|import)\\b)"
            );
            
            Matcher m = syntaxPattern.matcher(code);
            while (m.find()) {
                Style s = null;
                if (m.group("COMMENT") != null) s = cmt;
                else if (m.group("STRING") != null) s = str;
                else if (m.group("KEYWORD") != null) s = key;

                if (s != null) {
                    doc.setCharacterAttributes(m.start(), m.end() - m.start(), s, true);
                }
            }
        } catch (BadLocationException e) { e.printStackTrace(); }
    }

    // ... (Keep your initStyles and other methods) ...

    /**
     * 3. 🧠 UPDATED PARSER: Inserts Component instead of Text
     */
    private void parseAndAppend(String text) {
        if (text == null || text.isEmpty()) return;

        Matcher m = MASTER_PATTERN.matcher(text);
        int lastIndex = 0;

        while (m.find()) {
            // A. Text BEFORE the block
            String preText = text.substring(lastIndex, m.start());
            if (!preText.isEmpty()) append(preText, agentMsg);

            if (m.group(1) != null || m.group(2) != null) {
                // --- 💻 CODE BLOCK FOUND ---
                String content = m.group(2).trim(); // Trim newlines
                
                // Add newlines around the box for spacing
                append("\n", agentMsg); 
                
                // 🛑 INSERT COMPONENT
                insertComponent(createCodeComponent(content)); 
                
                append("\n", agentMsg);

            } else {
                // --- ➗ MATH BLOCK ---
                String mathRaw = m.group(3);
                String mathContent = extractMathContent(mathRaw);
                Icon icon = latexToIcon(mathContent);
                if (icon != null) {
                    insertIconDirectly(icon);
                } else {
                    append(mathRaw, agentMsg);
                }
            }
            lastIndex = m.end();
        }

        if (lastIndex < text.length()) {
            append(text.substring(lastIndex), agentMsg);
        }
    }

    private void insertComponent(java.awt.Component comp) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setComponent(attrs, comp);
            doc.insertString(doc.getLength(), " ", attrs); // Placeholder char
        } catch (BadLocationException e) { e.printStackTrace(); }
    }
    /**
     * 🖌️ THE HIGHLIGHTER ENGINE Breaks code into tokens and paints them
     * color-by-color.
     */
    private void appendSyntaxHighlightedCode(String code) {
        Matcher matcher = SYNTAX_PATTERN.matcher(code);
        int lastIdx = 0;

        while (matcher.find()) {
            // 1. Print plain code before the token (e.g. spaces, brackets)
            String plainText = code.substring(lastIdx, matcher.start());
            if (!plainText.isEmpty()) {
                append(plainText, normalStyle);
            }

            // 2. Colorize the Token
            if (matcher.group("COMMENT") != null) {
                append(matcher.group(), commentStyle);
            } else if (matcher.group("STRING") != null) {
                append(matcher.group(), stringStyle);
            } else if (matcher.group("KEYWORD") != null) {
                append(matcher.group(), keywordStyle);
            }

            lastIdx = matcher.end();
        }

        // 3. Print remaining code
        if (lastIdx < code.length()) {
            append(code.substring(lastIdx), normalStyle);
        }
    }

    private void append(String text, Style style) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException ignored) {
        }
    }

    private void insertIconDirectly(Icon icon) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            doc.insertString(doc.getLength(), " ", attrs);
        } catch (BadLocationException e) {
        }
    }

   

    private boolean looksLikeLatex(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("\\frac") || text.contains("\\sqrt") || text.contains("$$");
    }

    private String extractMathContent(String raw) {
        if (raw.startsWith("$$")) {
            return raw.substring(2, raw.length() - 2);
        }
        if (raw.startsWith("\\[")) {
            return raw.substring(2, raw.length() - 2);
        }
        if (raw.startsWith("\\(")) {
            return raw.substring(2, raw.length() - 2);
        }
        return raw;
    }

    /**
     * LATEX COLOR STRATEGY Uses a "Mid-Tone" color that is visible on both
     * Black and White.
     */
    private javax.swing.Icon latexToIcon(String latex) {
        try {
            if (latex == null || latex.trim().isEmpty()) {
                return null;
            }

            TeXFormula formula = new TeXFormula(latex);

            // 1. Create Icon
            org.scilab.forge.jlatexmath.TeXIcon icon = formula.createTeXIcon(
                    org.scilab.forge.jlatexmath.TeXConstants.STYLE_DISPLAY,
                    18
            );

            // 2. 🎨 SET COMMON COLOR: "Medium Slate Blue" (#7B68EE)
            // - On White Background: Clearly visible (Darker than sky blue)
            // - On Black Background: Clearly visible (Lighter than navy blue)
            // Alternative: new Color(0, 191, 255) (Deep Sky Blue)
            icon.setForeground(new java.awt.Color(123, 104, 238));

            // 3. Render to Image (Transparent Background)
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    icon.getIconWidth(),
                    icon.getIconHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB
            );

            java.awt.Graphics2D g2 = img.createGraphics();

            // Enable Antialiasing for sharp text
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            icon.paintIcon(null, g2, 0, 0);
            g2.dispose();

            return new javax.swing.ImageIcon(img);
        } catch (Exception e) {
            return null;
        }
    }
    
    private void snapToPosition(int index) {
        // Wrap in invokeLater to wait for TextPane to update its model
        javax.swing.SwingUtilities.invokeLater(() -> {
            // Wrap AGAIN to wait for Components (Code Blocks) to calculate their height
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    // 1. Get the pixel geometry of the text at 'index'
                    java.awt.Rectangle rect = chatPane.modelToView(index);
                    
                    if (rect != null) {
                        // 2. Force the Viewport to move to that Y position
                        // This aligns the text to the TOP of the window
                        jScrollPane1.getViewport().setViewPosition(new java.awt.Point(0, rect.y));
                    }
                } catch (BadLocationException e) {
                    // Ignore
                }
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
