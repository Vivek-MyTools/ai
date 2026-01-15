package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
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
    private static final Pattern MATH_PATTERN = Pattern.compile("(\\$\\$(?s:.*?)\\$\\$|\\\\\\((?s:.*?)\\\\\\)|\\\\\\[(?s:.*?)\\\\\\]|\\$(?!\\{)([^$]+?)\\$)");
    
    // FIX: Removed Degree (\u00B0), Celsius (\u2103), and Fahrenheit (\u2109) from this pattern.
    // They are text symbols, not emojis, and should use the main font to avoid "tofu" boxes.
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[\u2600-\u27BF\u2B00-\u2BFF\uFE00-\uFE0F]|[\uD800-\uDFFF]+");
    
    private static String cachedSymbolFont = null;

    public static JTextPane createCellComponent(String rawText, boolean isHeader, Color textColor, Color linkColor) {
        // Cleanup & Unicode normalization
        rawText = rawText.replace("<br>", "\n").replace("<br/>", "\n");
        boolean isBold = rawText.startsWith("**") && rawText.endsWith("**") && rawText.length() > 4;
        if (isBold) rawText = rawText.substring(2, rawText.length() - 2);
        boolean isItalic = !isBold && rawText.startsWith("*") && rawText.endsWith("*") && rawText.length() > 2;
        if (isItalic) rawText = rawText.substring(1, rawText.length() - 1);
        
        // Normalize degrees to standard forms
        rawText = rawText.replace("**", "").replace("\u2103", "\u00B0C").replace("\u2109", "\u00B0F").replace("\u00B0", "\u00B0");

        String displayText = rawText;
        String url = extractUrl(rawText);
        if (url != null) displayText = extractDisplay(rawText);

        JTextPane textComp = new JTextPane();
        textComp.setEditable(false);
        textComp.setOpaque(false);
        textComp.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));

        // --- ATTRIBUTES ---
        // Main font handles Text + Standard Symbols (Degree, C, F, Kannada, etc.)
        SimpleAttributeSet mainAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(mainAttrs, ChatThemeUtils.getUnicodeFontName());
        StyleConstants.setFontSize(mainAttrs, 14);

        // Symbol font handles strictly Emojis (Sun, Cloud, etc.)
        SimpleAttributeSet symbolAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(symbolAttrs, getSymbolFontName());
        StyleConstants.setFontSize(symbolAttrs, 14);

        if (isHeader || isBold) { StyleConstants.setBold(mainAttrs, true); StyleConstants.setBold(symbolAttrs, true); }
        else if (isItalic) { StyleConstants.setItalic(mainAttrs, true); StyleConstants.setItalic(symbolAttrs, true); }

        if (url != null) {
            StyleConstants.setForeground(mainAttrs, linkColor); StyleConstants.setForeground(symbolAttrs, linkColor);
            StyleConstants.setUnderline(mainAttrs, true); StyleConstants.setUnderline(symbolAttrs, true);
            textComp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final String targetUrl = url;
            textComp.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) ChatThemeUtils.openBrowser(targetUrl); }
            });
        } else {
            StyleConstants.setForeground(mainAttrs, textColor); StyleConstants.setForeground(symbolAttrs, textColor);
        }

        parseContent(textComp, displayText, mainAttrs, symbolAttrs);
        return textComp;
    }

    private static void parseContent(JTextPane textComp, String text, SimpleAttributeSet mainAttrs, SimpleAttributeSet symbolAttrs) {
        try {
            StyledDocument doc = textComp.getStyledDocument();
            Matcher mathMatcher = MATH_PATTERN.matcher(text);
            int lastIdx = 0;
            while (mathMatcher.find()) {
                String preText = text.substring(lastIdx, mathMatcher.start());
                if (!preText.isEmpty()) insertWithFallback(doc, preText, mainAttrs, symbolAttrs);
                
                String mathRaw = mathMatcher.group();
                String mathContent = MathRenderer.extractMathContent(mathRaw);
                Icon icon = MathRenderer.latexToIcon(mathRaw, mathContent);
                if (icon != null) { textComp.setCaretPosition(doc.getLength()); textComp.insertIcon(icon); doc.insertString(doc.getLength(), " ", mainAttrs); }
                else { doc.insertString(doc.getLength(), mathRaw, mainAttrs); }
                lastIdx = mathMatcher.end();
            }
            if (lastIdx < text.length()) insertWithFallback(doc, text.substring(lastIdx), mainAttrs, symbolAttrs);
            doc.setParagraphAttributes(0, doc.getLength(), mainAttrs, false);
        } catch (BadLocationException e) { textComp.setText(text); }
    }

    private static void insertWithFallback(StyledDocument doc, String text, SimpleAttributeSet mainAttrs, SimpleAttributeSet symbolAttrs) throws BadLocationException {
        Matcher matcher = SYMBOL_PATTERN.matcher(text);
        int lastIdx = 0;
        while (matcher.find()) {
            doc.insertString(doc.getLength(), text.substring(lastIdx, matcher.start()), mainAttrs);
            // Only characters strictly matching the Emoji/Symbol pattern use the fallback font
            doc.insertString(doc.getLength(), matcher.group(), symbolAttrs);
            lastIdx = matcher.end();
        }
        doc.insertString(doc.getLength(), text.substring(lastIdx), mainAttrs);
    }
    
    private static String getSymbolFontName() {
        if (cachedSymbolFont != null) return cachedSymbolFont;
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        Arrays.sort(available);
        if (Arrays.binarySearch(available, "Segoe UI Emoji") >= 0) cachedSymbolFont = "Segoe UI Emoji";
        else if (Arrays.binarySearch(available, "Segoe UI Symbol") >= 0) cachedSymbolFont = "Segoe UI Symbol";
        else cachedSymbolFont = Font.DIALOG;
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