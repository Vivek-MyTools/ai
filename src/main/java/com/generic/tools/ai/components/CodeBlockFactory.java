package com.generic.tools.ai.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class CodeBlockFactory {

    // --- COLORS ---
    private static final Color COL_KEYWORD  = new Color(204, 120, 50);
    private static final Color COL_STRING   = new Color(106, 135, 89);
    private static final Color COL_COMMENT  = new Color(128, 128, 128);
    private static final Color COL_TAG      = new Color(255, 198, 109);
    private static final Color COL_NORMAL   = new Color(169, 183, 198);

    private static final Color COL_KEYWORD_L = new Color(0, 51, 179);
    private static final Color COL_STRING_L  = new Color(6, 125, 23);
    private static final Color COL_COMMENT_L = new Color(128, 128, 128);
    private static final Color COL_TAG_L     = new Color(0, 0, 128);
    private static final Color COL_NORMAL_L  = Color.BLACK;

    public static JPanel createCodeComponent(String codeContent, Color parentBg, Color parentFg) {
        JPanel container = new JPanel(new BorderLayout());
        container.putClientProperty("isCodeBlock", true);
        
        String cleanCode = codeContent.replace('\u00A0', ' ').replace("\t", "    ");
        container.putClientProperty("rawCode", cleanCode); 

        boolean isDark = ChatThemeUtils.isDarkTheme(parentBg);
        Color codeBg = ChatThemeUtils.getCodeBackgroundColor(parentBg);
        Color borderColor = isDark ? codeBg.brighter() : codeBg.darker();

        container.setBackground(codeBg);
        container.setBorder(BorderFactory.createLineBorder(borderColor, 1));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(getDynamicHeaderColor(codeBg, isDark));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JLabel langLabel = new JLabel("Code");
        langLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        langLabel.setForeground(isDark ? Color.LIGHT_GRAY : Color.DARK_GRAY);
        
        JLabel copyBtn = new JLabel("📋 Copy");
        copyBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        copyBtn.setForeground(isDark ? Color.WHITE : Color.BLACK);
        copyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        copyBtn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(cleanCode), null);
                    copyBtn.setText("✔ Copied!");
                    Timer t = new Timer(1500, evt -> copyBtn.setText("📋 Copy"));
                    t.setRepeats(false); t.start();
                } catch (Exception ex) { copyBtn.setText("❌ Error"); }
            }
            public void mouseEntered(MouseEvent e) { copyBtn.setForeground(new Color(0, 120, 215)); }
            public void mouseExited(MouseEvent e) { 
                Color currentHeaderBg = copyBtn.getParent().getBackground();
                boolean currentHeaderIsDark = ChatThemeUtils.isDarkTheme(currentHeaderBg);
                copyBtn.setForeground(currentHeaderIsDark ? Color.WHITE : Color.BLACK); 
            }
        });

        headerPanel.add(langLabel, BorderLayout.WEST);
        headerPanel.add(copyBtn, BorderLayout.EAST);
        container.add(headerPanel, BorderLayout.NORTH);

        JTextPane codePane = new JTextPane();
        codePane.setEditable(false);
        codePane.setBackground(codeBg);
        codePane.setForeground(isDark ? COL_NORMAL : COL_NORMAL_L);
        codePane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        codePane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        SwingUtilities.invokeLater(() -> highlightCode(codePane, cleanCode, isDark));

        container.add(codePane, BorderLayout.CENTER);
        return container;
    }

    /**
     * Refactors the refresh logic out of ChatMessagesPanel.
     */
    public static void refreshCodeBlock(JPanel panel, Color currentBg, Color currentFg) {
        String rawCode = (String) panel.getClientProperty("rawCode");
        boolean isDark = ChatThemeUtils.isDarkTheme(currentBg);
        Color codeBg = ChatThemeUtils.getCodeBackgroundColor(currentBg);
        Color borderCol = isDark ? codeBg.brighter() : codeBg.darker();

        // 1. Update Wrapper
        panel.setBackground(codeBg);
        panel.setBorder(BorderFactory.createLineBorder(borderCol, 1));

        // 2. Iterate Children
        for (Component child : panel.getComponents()) {
            if (child instanceof JTextPane) {
                JTextPane innerPane = (JTextPane) child;
                innerPane.setBackground(codeBg);
                innerPane.setForeground(currentFg);
                highlightCode(innerPane, rawCode, isDark);
            }
            else if (child instanceof JPanel) {
                JPanel header = (JPanel) child;
                header.setBackground(getDynamicHeaderColor(codeBg, isDark));
                
                for (Component headerComp : header.getComponents()) {
                    if (headerComp instanceof JLabel) {
                        JLabel label = (JLabel) headerComp;
                        String text = label.getText();
                        if ("Code".equals(text)) {
                            label.setForeground(isDark ? Color.LIGHT_GRAY : Color.DARK_GRAY);
                        } else if (text != null && (text.contains("Copy") || text.contains("Copied"))) {
                            label.setForeground(isDark ? Color.WHITE : Color.BLACK);
                        }
                    }
                }
            }
        }
    }

    private static Color getDynamicHeaderColor(Color codeBg, boolean isDark) {
        if (isDark) {
            return new Color(
                Math.min(255, codeBg.getRed() + 25), 
                Math.min(255, codeBg.getGreen() + 25), 
                Math.min(255, codeBg.getBlue() + 25)
            );
        } else {
            return new Color(
                Math.max(0, codeBg.getRed() - 15), 
                Math.max(0, codeBg.getGreen() - 15), 
                Math.max(0, codeBg.getBlue() - 15)
            );
        }
    }

    public static void highlightCode(JTextPane pane, String code, boolean isDark) {
        pane.setText(""); 
        StyledDocument doc = pane.getStyledDocument();
        
        Color cKeyword = isDark ? COL_KEYWORD : COL_KEYWORD_L;
        Color cString  = isDark ? COL_STRING  : COL_STRING_L;
        Color cComment = isDark ? COL_COMMENT : COL_COMMENT_L;
        Color cTag     = isDark ? COL_TAG     : COL_TAG_L;
        Color cNormal  = isDark ? COL_NORMAL  : COL_NORMAL_L;

        SimpleAttributeSet baseAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(baseAttr, cNormal);
        StyleConstants.setFontFamily(baseAttr, Font.MONOSPACED);
        StyleConstants.setFontSize(baseAttr, 12);
        
        try {
            doc.insertString(0, code, baseAttr);
            
            // 1. KEYWORDS
            String keywords = "\\b(public|private|protected|class|interface|enum|void|int|double|float|char|boolean|String|if|else|switch|case|default|while|for|do|break|continue|return|try|catch|finally|throw|throws|new|this|super|static|final|abstract|synchronized|volatile|transient|extends|implements|import|package|true|false|null|var|let|const|function|def|return|await|async)\\b";
            applyStyle(doc, keywords, cKeyword, true, false);
            // 2. TAGS
            applyStyle(doc, "(</?[a-zA-Z][\\w:-]*)", cTag, true, false); 
            // 3. STRINGS
            applyStyle(doc, "(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*')", cString, false, false);
            // 4. COMMENTS
            applyStyle(doc, "(//.*|#.*)", cComment, false, true); 
            applyStyle(doc, "(/\\*[\\s\\S]*?\\*/|)", cComment, false, true);  
        } catch (Exception e) { e.printStackTrace(); pane.setText(code); }
    }

    private static void applyStyle(StyledDocument doc, String regex, Color color, boolean bold, boolean italic) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(getText(doc));
        MutableAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, color);
        if (bold) StyleConstants.setBold(attr, true);
        if (italic) StyleConstants.setItalic(attr, true);
        while (m.find()) { doc.setCharacterAttributes(m.start(), m.end() - m.start(), attr, false); }
    }

    private static String getText(StyledDocument doc) {
        try { return doc.getText(0, doc.getLength()); } catch (Exception e) { return ""; }
    }
}