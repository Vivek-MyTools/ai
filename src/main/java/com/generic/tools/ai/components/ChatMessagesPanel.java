package com.generic.tools.ai.components;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

/**
 *
 * @author vivek
 */
public class ChatMessagesPanel extends javax.swing.JPanel {

    private Style agentName, agentMsg, userName, userMsg;
    
    private int lastUserIndex = 0;
    
    private static final java.util.regex.Pattern MIXED_MATH_PATTERN = 
        java.util.regex.Pattern.compile("(?s)(.*?)(\\$?\\$|\\\\\\[|\\\\\\()(.+?)(\\$?\\$|\\\\\\]|\\\\\\))(.*)");
    
    /**
     * Creates new form ChatMessagePanel
     */
    public ChatMessagesPanel() {
        initComponents();
        initStyles();
        setFonts();
    }
    
    private void setFonts() {
        // --- 1. SMART FONT SELECTION ---
        // "Nirmala UI" is built into Windows 10/11 for Hindi/Kannada/Telugu etc.
        // "Dialog" is a Java logical font that auto-finds the right language font on Linux/Mac.
        String fontName = "Dialog"; // Default fallback
        java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();

        for (String f : availableFonts) {
            if (f.equals("Nirmala UI")) {
                fontName = "Nirmala UI"; // Best for Windows Indian scripts
                break;
            } else if (f.equals("Arial Unicode MS")) {
                fontName = "Arial Unicode MS"; // Good fallback
            }
        }

        Font unicodeFont = new Font(fontName, Font.PLAIN, 14);

        // --- 2. APPLY FONT ---
        chatPane.setFont(unicodeFont);
       
    }

    private void initStyles() {
        StyledDocument doc = chatPane.getStyledDocument();

        agentName = chatPane.addStyle("AgentName", null);
        StyleConstants.setBold(agentName, true);

        agentMsg = chatPane.addStyle("AgentMsg", null);

        userName = chatPane.addStyle("UserName", null);
        StyleConstants.setBold(userName, true);

        userMsg = chatPane.addStyle("UserMsg", null);
    }

    // ===== Public API =====
    public void addUserMessage(String msg) {
        lastUserIndex = chatPane.getDocument().getLength();
        append("You: ", userName);
        append(msg + "\n\n", userMsg);
        scrollToIndex(lastUserIndex);
    }

    public void addAgentMessage(String msg) {        
        append("Agent: ", agentName);
        parseAndAppend(msg);
        append("\n\n", agentMsg);        
        scrollToIndex(lastUserIndex);
    }
    
    private void parseAndAppend(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        java.util.regex.Matcher m = MIXED_MATH_PATTERN.matcher(text);

        if (m.find()) {
            // A. Pre-text
            String preText = m.group(1);
            if (!preText.isEmpty()) {
                append(preText, agentMsg);
            }

            // B. Math
            String mathContent = m.group(3);
            if (mathContent.contains("\\\\")) {
                mathContent = mathContent.replace("\\\\", "\\");
            }

            Icon icon = latexToIcon(mathContent);
            if (icon != null) {
                // ✅ USE NEW HELPER (Does not scroll)
                insertIconDirectly(icon);
            } else {
                append(m.group(2) + mathContent + m.group(4), agentMsg);
            }

            // C. Remaining
            String remaining = m.group(5);
            parseAndAppend(remaining);

        } else {
            append(text, agentMsg);
        }
    }

    private void append(String text, Style style) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException ignored) {
        }
    }
    
    private boolean looksLikeLatex(String text) {
        if (text == null) return false;
        return text.contains("\\frac") || text.contains("\\sqrt") || text.contains("$$"); 
    }

    private void addLatex(String latex) {
        Icon icon = latexToIcon(latex);

        // 🛑 FALLBACK: If rendering failed (e.g., due to '@'), just show plain text
        if (icon == null) {
            append("Agent: ", agentName);
            append(latex + "\n\n", agentMsg);
            return;
        }

        try {
            StyledDocument doc = chatPane.getStyledDocument();
            doc.insertString(doc.getLength(), "Agent: ", agentName);
           // chatPane.setCaretPosition(doc.getLength());
            chatPane.insertIcon(icon);
            doc.insertString(doc.getLength(), "\n\n", agentMsg);
        } catch (BadLocationException ignored) {
        }
    }
    
    private void scrollToIndex(int index) {
        SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    // 1. Get the exact Y coordinate of the User Message
                    java.awt.Rectangle rect = chatPane.modelToView(index);
                    
                    if (rect != null) {
                        // 2. Calculate the "Max Possible" scroll position
                        // (Total Height - Window Height)
                        int viewportHeight = jScrollPane1.getViewport().getHeight();
                        int totalHeight = chatPane.getHeight();
                        int maxScrollY = totalHeight - viewportHeight;
                        
                        // 3. Determine target Y
                        // We want 'rect.y', but we can't go past 'maxScrollY'
                        int finalY = Math.min(rect.y, maxScrollY);
                        
                        // 4. Force the view to that position (Avoid negative values)
                        if (finalY < 0) finalY = 0;
                        
                        jScrollPane1.getViewport().setViewPosition(new java.awt.Point(0, finalY));
                    }
                } catch (BadLocationException e) {
                    // Ignore
                }
            });
        });
    }
    
    private void insertIconDirectly(Icon icon) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();

            // Create a style that effectively turns a character into the icon
            javax.swing.text.SimpleAttributeSet attrs = new javax.swing.text.SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);

            // Insert a "space" that acts as the container for the icon
            doc.insertString(doc.getLength(), " ", attrs);

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private Icon latexToIcon(String latex) {
        try {
            if (latex == null || latex.trim().isEmpty()) return null;

            TeXFormula formula = new TeXFormula(latex);
            
            // 1. Create the Icon
            TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 18);
            
            // 2. 🎨 CHANGE COLOR HERE
            // Option A: Hardcoded Color (e.g., Dark Blue for Math)
            icon.setForeground(new java.awt.Color(70, 130, 180));
            
            // Option B: Match the Chat Text Color (Dynamic)
            // icon.setForeground(chatPane.getForeground());

            // 3. Render to Image
            BufferedImage img = new BufferedImage(
                    icon.getIconWidth(),
                    icon.getIconHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g2 = img.createGraphics();
            
            // Optional: Improve render quality
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                                
            icon.paintIcon(null, g2, 0, 0);
            g2.dispose();

            return new ImageIcon(img);
        } catch (Exception e) {
            return null;
        }
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
        chatPane.setFont(new java.awt.Font("SansSerif", 0, 14)); // NOI18N
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
