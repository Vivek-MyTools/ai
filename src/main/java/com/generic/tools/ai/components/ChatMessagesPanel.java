package com.generic.tools.ai.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.scilab.forge.jlatexmath.ParseException;
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
    
    private Color getCodeBackgroundColor() {
        Color base = chatPane.getBackground();
        boolean isDark = isDarkTheme(); // Use our existing helper
        
        // If Dark Theme: Lighten the background by 10%
        // If Light Theme: Darken the background by 5%
        float factor = isDark ? 1.10f : 0.95f; 
        
        int r = Math.min(255, (int) (base.getRed() * factor));
        int g = Math.min(255, (int) (base.getGreen() * factor));
        int b = Math.min(255, (int) (base.getBlue() * factor));
        
        return new Color(r, g, b);
    }
    
    private boolean isDarkTheme() {
        Color bg = chatPane.getBackground();
        // Calculate brightness (0.0 to 1.0)
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return brightness < 0.5;
    }

    public void initStyles() {
        
        java.awt.Point savedScrollPosition = (jScrollPane1 != null && jScrollPane1.getViewport() != null) 
                ? jScrollPane1.getViewport().getViewPosition() 
                : new java.awt.Point(0,0);
        
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
        
        refreshAllCodeComponents();
        
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (jScrollPane1 != null && jScrollPane1.getViewport() != null) {
                jScrollPane1.getViewport().setViewPosition(savedScrollPosition);
            }
        });
    }
    
    private void refreshAllCodeComponents() {
        StyledDocument doc = chatPane.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        
        // Loop through all elements (paragraphs/lines) in the document
        for (int i = 0; i < root.getElementCount(); i++) {
            Element line = root.getElement(i);
            
            // Loop through "leaves" (individual chunks of text or components)
            for (int j = 0; j < line.getElementCount(); j++) {
                Element leaf = line.getElement(j);
                AttributeSet attrs = leaf.getAttributes();
                
                // Check if this chunk is a Component
                java.awt.Component comp = StyleConstants.getComponent(attrs);
                
                // If it's a JPanel and it's one of OUR code blocks...
                if (comp instanceof JPanel && ((JPanel) comp).getClientProperty("isCodeBlock") != null) {
                    // Update it!
                    updateCodeBlockTheme((JPanel) comp);
                }
            }
        }
        snapToPosition(lastConversationIndex);
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
        boolean isDark = isDarkTheme();

        // 🎨 1. Define Colors Based on Theme
        Color cardBg = isDark ? new Color(40, 44, 52)     : new Color(245, 245, 245); // VS Code Dark vs Light Gray
        Color cardFg = isDark ? new Color(220, 220, 220)  : Color.BLACK;              // White Text vs Black Text

        // A. The Inner Code Editor
        JTextPane codePane = new JTextPane();
        codePane.setEditable(false);
        codePane.setFont(new Font("Consolas", Font.PLAIN, 13));
        codePane.setBackground(cardBg);
        codePane.setForeground(cardFg);
        
        // B. Apply Syntax Highlighting (Pass theme flag)
        highlightCodeInComponent(codePane, codeContent, isDark);

        JPanel wrapper = new JPanel(new BorderLayout());
        
        // 🏷️ TAGGING: Mark this panel as a "CodeBlock" so we can find it later
        wrapper.putClientProperty("isCodeBlock", true); 
        wrapper.putClientProperty("rawCode", codeContent); // Save raw code for re-highlighting

        wrapper.add(codePane, BorderLayout.CENTER);
        
        // Apply initial theme
        updateCodeBlockTheme(wrapper); 

        return wrapper;
    } 
   
    private void updateCodeBlockTheme(JPanel wrapper) {
        // 1. Get Dynamic Colors
        boolean isDark = isDarkTheme();
        Color cardBg = getCodeBackgroundColor(); // 👈 DYNAMICALLY CALCULATED
        Color cardFg = chatPane.getForeground(); // Matches main text color
        
        // Calculate a border color (Just slightly more contrast than the bg)
        Color borderCol = isDark ? cardBg.brighter() : cardBg.darker();

        // 2. Update Wrapper (The Box)
        wrapper.setBackground(cardBg);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, borderCol), 
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // 3. Update Inner TextPane
        JTextPane codePane = (JTextPane) wrapper.getComponent(0);
        codePane.setBackground(cardBg);
        codePane.setForeground(cardFg);

        // 4. Re-Apply Syntax Highlighting
        // We pass 'isDark' so the keywords (blue/orange) switch palettes correctly
        String rawCode = (String) wrapper.getClientProperty("rawCode");
        codePane.setText(""); 
        highlightCodeInComponent(codePane, rawCode, isDark); 
    }
   
    private void highlightCodeInComponent(JTextPane pane, String code, boolean isDark) {
        StyledDocument doc = pane.getStyledDocument();
        
        // 🎨 2. Define Syntax Colors Based on Theme
        Color keyColor, strColor, cmtColor;

        if (isDark) {
            // VS Code Dark Palette
            keyColor = new Color(86, 156, 214);   // Light Blue
            strColor = new Color(206, 145, 120);  // Soft Orange/Red
            cmtColor = new Color(106, 153, 85);   // Muted Green
        } else {
            // Standard Light Palette
            keyColor = new Color(0, 0, 180);      // Dark Blue
            strColor = new Color(0, 100, 0);      // Dark Green
            cmtColor = new Color(100, 100, 100);  // Gray
        }

        // Create Styles
        Style def = pane.addStyle("Default", null);
        StyleConstants.setForeground(def, pane.getForeground());

        Style key = pane.addStyle("Keyword", null);
        StyleConstants.setForeground(key, keyColor);
        StyleConstants.setBold(key, true);

        Style str = pane.addStyle("String", null);
        StyleConstants.setForeground(str, strColor);

        Style cmt = pane.addStyle("Comment", null);
        StyleConstants.setForeground(cmt, cmtColor);
        StyleConstants.setItalic(cmt, true);

        try {
            doc.insertString(0, code, def);

            // Parse (Regex remains the same)
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
        } catch (BadLocationException e) {}
    }

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
        } catch (BadLocationException e) {}
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
        } catch (ParseException e) {
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
