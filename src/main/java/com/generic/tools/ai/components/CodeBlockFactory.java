package com.generic.tools.ai.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class CodeBlockFactory {

    private static final Pattern SYNTAX_PATTERN = Pattern.compile(
            "(?<COMMENT>//.*|/\\*(?s:.*?)\\*/)|(?<STRING>\"([^\"\\\\]|\\\\.)*\")|(?<KEYWORD>\\b(public|private|protected|class|static|final|void|int|double|new|return|if|else|for|while|import)\\b)"
    );

    public static JPanel createCodeComponent(String codeContent, Color parentBg, Color parentFg) {
        boolean isDark = ChatThemeUtils.isDarkTheme(parentBg);
        
        // Colors
        Color cardBg = ChatThemeUtils.getCodeBackgroundColor(parentBg);
        Color borderCol = isDark ? cardBg.brighter() : cardBg.darker();

        // Inner Code Pane
        JTextPane codePane = new JTextPane();
        codePane.setEditable(false);
        codePane.setFont(new Font("Consolas", Font.PLAIN, 13));
        codePane.setBackground(cardBg);
        codePane.setForeground(parentFg);

        highlightCode(codePane, codeContent, isDark);

        // Wrapper Panel
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.putClientProperty("isCodeBlock", true);
        wrapper.putClientProperty("rawCode", codeContent); // Store for potential re-theming
        wrapper.setBackground(cardBg);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, borderCol),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        wrapper.add(codePane, BorderLayout.CENTER);

        return wrapper;
    }

    public static void highlightCode(JTextPane pane, String code, boolean isDark) {
        StyledDocument doc = pane.getStyledDocument();
        pane.setText(""); // Clear previous

        // Define Colors
        Color keyColor = isDark ? new Color(86, 156, 214) : new Color(0, 0, 180);
        Color strColor = isDark ? new Color(206, 145, 120) : new Color(0, 100, 0);
        Color cmtColor = isDark ? new Color(106, 153, 85) : new Color(100, 100, 100);

        // Define Styles
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
            Matcher m = SYNTAX_PATTERN.matcher(code);
            while (m.find()) {
                Style s = null;
                if (m.group("COMMENT") != null) s = cmt;
                else if (m.group("STRING") != null) s = str;
                else if (m.group("KEYWORD") != null) s = key;

                if (s != null) {
                    doc.setCharacterAttributes(m.start(), m.end() - m.start(), s, true);
                }
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
}