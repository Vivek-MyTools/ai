package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Handles the visual rendering of a single table cell (Math, Links, Fonts).
 */
public class TableCellRenderer {

    private static final Pattern LINK_PATTERN = Pattern.compile(
        "<?(https?://[^\\s]+)>?|" +              
        "\\[(.*?)\\]\\((https?://.*?)\\)"       
    );

    private static final Pattern MATH_PATTERN = Pattern.compile(
        "(\\$\\$(?s:.*?)\\$\\$|\\\\\\((?s:.*?)\\\\\\)|\\\\\\[(?s:.*?)\\\\\\]|\\$(?!\\{)([^$]+?)\\$)"
    );

    public static JTextPane createCellComponent(String rawText, boolean isHeader, Color textColor, Color linkColor) {
        // 1. Cleanup & Formatting Detection
        rawText = rawText.replace("<br>", "\n").replace("<br/>", "\n");
        
        boolean isBold = false;
        if (rawText.startsWith("**") && rawText.endsWith("**") && rawText.length() > 4) {
            isBold = true;
            rawText = rawText.substring(2, rawText.length() - 2); 
        }
        rawText = rawText.replace("**", "");

        boolean isItalic = false;
        if (!isBold && rawText.startsWith("*") && rawText.endsWith("*") && rawText.length() > 2) {
            isItalic = true;
            rawText = rawText.substring(1, rawText.length() - 1);
        }

        // Handle common symbol replacements
        rawText = rawText.replace("\u2103", "°C").replace("\u2109", "°F").replace("\u00B0", "°");

        // 2. Link Parsing
        String displayText = rawText;
        String url = null;
        Matcher m = LINK_PATTERN.matcher(rawText);
        if (m.find()) {
            if (m.group(1) != null) {
                url = m.group(1);
                displayText = url;
            } else if (m.group(2) != null && m.group(3) != null) {
                displayText = m.group(2);
                url = m.group(3);
            }
        }

        // 3. Component Setup
        JTextPane textComp = new JTextPane();
        textComp.setEditable(false);
        textComp.setOpaque(false);
        
        // 🔧 CRITICAL FIX: Use Font.DIALOG for the Base Component.
        // This ensures fallback support for Math/Emojis works at the component level.
        textComp.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        
        // 🔧 CRITICAL FIX: Use Font.DIALOG for Attributes.
        // We DO NOT fetch 'safeFontName' (like Nirmala UI) here because it forces
        // a specific font that might lack Math symbols. Dialog allows the JVM to 
        // mix and match fonts (Indic + Math) automatically.
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, Font.DIALOG);
        StyleConstants.setFontSize(attrs, 14);
        
        if (isHeader || isBold) StyleConstants.setBold(attrs, true);
        else if (isItalic) StyleConstants.setItalic(attrs, true);
        
        // Color & Link Interaction
        if (url != null) {
            StyleConstants.setForeground(attrs, linkColor);
            StyleConstants.setUnderline(attrs, true);
            textComp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            
            final String targetUrl = url;
            textComp.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        ChatThemeUtils.openBrowser(targetUrl);
                    }
                }
            });
        } else {
            StyleConstants.setForeground(attrs, textColor);
        }

        // 4. Content Parsing (Text + Math)
        parseContent(textComp, displayText, attrs);

        return textComp;
    }

    private static void parseContent(JTextPane textComp, String text, SimpleAttributeSet attrs) {
        try {
            StyledDocument doc = textComp.getStyledDocument();
            Matcher mathMatcher = MATH_PATTERN.matcher(text);
            int lastIdx = 0;
            
            while (mathMatcher.find()) {
                String preText = text.substring(lastIdx, mathMatcher.start());
                if (!preText.isEmpty()) doc.insertString(doc.getLength(), preText, attrs);
                
                String mathRaw = mathMatcher.group();
                String mathContent = MathRenderer.extractMathContent(mathRaw);
                Icon icon = MathRenderer.latexToIcon(mathRaw, mathContent);
                
                if (icon != null) {
                    textComp.setCaretPosition(doc.getLength());
                    textComp.insertIcon(icon);
                    doc.insertString(doc.getLength(), " ", attrs); 
                } else {
                    doc.insertString(doc.getLength(), mathRaw, attrs);
                }
                lastIdx = mathMatcher.end();
            }
            
            if (lastIdx < text.length()) {
                doc.insertString(doc.getLength(), text.substring(lastIdx), attrs);
            }
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);

        } catch (BadLocationException ex) {
            textComp.setText(text);
        }
    }
}