package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
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

public class TableCellRenderer {

    private static final Pattern LINK_PATTERN = Pattern.compile("<?(https?://[^\\s]+)>?|\\[(.*?)\\]\\((https?://.*?)\\)");
    
    // REGEX: Matches Code (`...`) OR Math ($$, \(, \[, $)
    // We prioritize Code match so Math delimiters inside Code are ignored.
    private static final Pattern CELL_MASTER_PATTERN = Pattern.compile(
        "(`([^`]+)`)|" + 
        "((\\$\\$(?s:.*?)\\$\\$|\\\\\\((?s:.*?)\\\\\\)|\\\\\\[(?s:.*?)\\\\\\]|\\$(?!\\{)([^$]+?)\\$))" 
    );

    private static String cachedSymbolFont = null;

    public static JTextPane createCellComponent(String rawText, boolean isHeader, Color textColor, Color linkColor) {
        rawText = rawText.replace("\u00A0", " ").replace("\uFE0F", "");
        rawText = rawText.replace("<br>", "\n").replace("<br/>", "\n").trim();
        
        boolean isBold = rawText.startsWith("**") && rawText.endsWith("**") && rawText.length() > 4;
        if (isBold) rawText = rawText.substring(2, rawText.length() - 2);
        boolean isItalic = !isBold && rawText.startsWith("*") && rawText.endsWith("*") && rawText.length() > 2;
        if (isItalic) rawText = rawText.substring(1, rawText.length() - 1);
        
        rawText = rawText.replace("**", "").replace("\u2103", "\u00B0C").replace("\u2109", "\u00B0F").replace("\u00B0", "\u00B0");

        String displayText = rawText;
        String url = extractUrl(rawText);
        if (url != null) displayText = extractDisplay(rawText);

        JTextPane textComp = new JTextPane();
        textComp.setEditable(false);
        textComp.setOpaque(false);
        textComp.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));

        // --- ATTRIBUTES ---
        SimpleAttributeSet mainAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(mainAttrs, ChatThemeUtils.getSmartFontName(displayText));
        StyleConstants.setFontSize(mainAttrs, 14);

        SimpleAttributeSet codeAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(codeAttrs, "Monospaced");
        StyleConstants.setFontSize(codeAttrs, 14);
        StyleConstants.setBackground(codeAttrs, new Color(128, 128, 128, 40)); 

        SimpleAttributeSet emojiAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(emojiAttrs, "Segoe UI Emoji");
        StyleConstants.setFontSize(emojiAttrs, 14);

        SimpleAttributeSet degreeAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(degreeAttrs, "Segoe UI Symbol"); 
        StyleConstants.setFontSize(degreeAttrs, 14);

        if (isHeader || isBold) { 
            StyleConstants.setBold(mainAttrs, true); StyleConstants.setBold(codeAttrs, true);
            StyleConstants.setBold(emojiAttrs, true); StyleConstants.setBold(degreeAttrs, true);
        } else if (isItalic) { 
            StyleConstants.setItalic(mainAttrs, true); StyleConstants.setItalic(codeAttrs, true);
            StyleConstants.setItalic(emojiAttrs, true); StyleConstants.setItalic(degreeAttrs, true);
        }

        if (url != null) {
            Color c = linkColor;
            StyleConstants.setForeground(mainAttrs, c); StyleConstants.setForeground(emojiAttrs, c); StyleConstants.setForeground(degreeAttrs, c);
            StyleConstants.setUnderline(mainAttrs, true); StyleConstants.setUnderline(emojiAttrs, true); StyleConstants.setUnderline(degreeAttrs, true);
            textComp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final String targetUrl = url;
            textComp.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) ChatThemeUtils.openBrowser(targetUrl); }
            });
        } else {
            Color c = textColor;
            StyleConstants.setForeground(mainAttrs, c); StyleConstants.setForeground(codeAttrs, c); 
            StyleConstants.setForeground(emojiAttrs, c); StyleConstants.setForeground(degreeAttrs, c);
        }

        parseContent(textComp, displayText, mainAttrs, codeAttrs, emojiAttrs, degreeAttrs);
        return textComp;
    }

    private static void parseContent(JTextPane textComp, String text, SimpleAttributeSet mainAttrs, SimpleAttributeSet codeAttrs, SimpleAttributeSet emojiAttrs, SimpleAttributeSet degreeAttrs) {
        try {
            StyledDocument doc = textComp.getStyledDocument();
            Matcher matcher = CELL_MASTER_PATTERN.matcher(text);
            int lastIdx = 0;
            
            while (matcher.find()) {
                String preText = text.substring(lastIdx, matcher.start());
                if (!preText.isEmpty()) insertWithFallback(doc, preText, mainAttrs, emojiAttrs, degreeAttrs);
                
                // Group 1: Code (`...`)
                if (matcher.group(1) != null) {
                    String codeContent = matcher.group(2); 
                    doc.insertString(doc.getLength(), codeContent, codeAttrs);
                } 
                // Group 3: Math ($...$ etc)
                else {
                    String mathRaw = matcher.group(); 
                    String mathContent = MathRenderer.extractMathContent(mathRaw);
                    
                    Icon icon = MathRenderer.latexToIcon(mathRaw, mathContent, StyleConstants.getForeground(mainAttrs));
                    
                    if (icon != null) { 
                        // STORE METADATA FOR THEME REFRESH
                        SimpleAttributeSet iconAttrs = new SimpleAttributeSet();
                        StyleConstants.setIcon(iconAttrs, icon);
                        iconAttrs.addAttribute("LatexRaw", mathRaw);
                        iconAttrs.addAttribute("LatexContent", mathContent);
                        
                        doc.insertString(doc.getLength(), " ", iconAttrs); 
                        doc.insertString(doc.getLength(), " ", mainAttrs); 
                    } else { 
                        // Fallback: If parsing fails (e.g. invalid syntax), show raw text
                        doc.insertString(doc.getLength(), mathRaw, mainAttrs); 
                    }
                }
                lastIdx = matcher.end();
            }
            if (lastIdx < text.length()) insertWithFallback(doc, text.substring(lastIdx), mainAttrs, emojiAttrs, degreeAttrs);
            
            doc.setParagraphAttributes(0, doc.getLength(), mainAttrs, false);
        } catch (BadLocationException e) { textComp.setText(text); }
    }

    private static void insertWithFallback(StyledDocument doc, String text, SimpleAttributeSet mainAttrs, SimpleAttributeSet emojiAttrs, SimpleAttributeSet degreeAttrs) throws BadLocationException {
        Pattern specialPattern = Pattern.compile("([\u2600-\u27BF\u2B00-\u2BFF\uFE00-\uFE0F]+)|([\u00B0\u2103\u2109])");
        Matcher matcher = specialPattern.matcher(text);
        int lastIdx = 0;

        while (matcher.find()) {
            doc.insertString(doc.getLength(), text.substring(lastIdx, matcher.start()), mainAttrs);
            if (matcher.group(1) != null) {
                doc.insertString(doc.getLength(), matcher.group(1), emojiAttrs); 
            } else {
                doc.insertString(doc.getLength(), matcher.group(2), degreeAttrs); 
            }
            lastIdx = matcher.end();
        }
        doc.insertString(doc.getLength(), text.substring(lastIdx), mainAttrs);
    }
    
    private static String getSymbolFontName() {
        if (cachedSymbolFont != null) return cachedSymbolFont;
        cachedSymbolFont = Font.DIALOG; 
        return cachedSymbolFont;
    }

    private static String extractUrl(String text) {
        Matcher m = LINK_PATTERN.matcher(text);
        if (m.find()) return (m.group(1) != null) ? m.group(1) : m.group(3);
        return null;
    }
    private static String extractDisplay(String text) {
        Matcher m = LINK_PATTERN.matcher(text);
        if (m.find()) return (m.group(1) != null) ? m.group(1) : m.group(2);
        return text;
    }
}