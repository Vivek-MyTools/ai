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
    
    // FIX: Combined Pattern to detect Emojis AND Degrees separately
    // Group 1: Emojis (Sun, Cloud, etc.)
    // Group 2: Degrees (°, ℃, ℉)
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile(
        "(?:" + 
        "([\\u2600-\\u27BF\\u2B00-\\u2BFF\\uFE00-\\uFE0F]|[\\uD800-\\uDFFF]+)" + // Group 1: Emojis
        "|" +
        "([\\u00B0\\u2103\\u2109])" + // Group 2: Degrees
        ")"
    );
    
    private static String cachedSymbolFont = null;

    public static JTextPane createCellComponent(String rawText, boolean isHeader, Color textColor, Color linkColor) {
        // Sanitize
        rawText = rawText.replace("\u00A0", " ").replace("\uFE0F", "");
        rawText = rawText.replace("<br>", "\n").replace("<br/>", "\n").trim();
        
        boolean isBold = rawText.startsWith("**") && rawText.endsWith("**") && rawText.length() > 4;
        if (isBold) rawText = rawText.substring(2, rawText.length() - 2);
        boolean isItalic = !isBold && rawText.startsWith("*") && rawText.endsWith("*") && rawText.length() > 2;
        if (isItalic) rawText = rawText.substring(1, rawText.length() - 1);
        
        // Normalize: Force standard degree symbol
        rawText = rawText.replace("**", "").replace("\u2103", "\u00B0C").replace("\u2109", "\u00B0F").replace("\u00B0", "\u00B0");

        String displayText = rawText;
        String url = extractUrl(rawText);
        if (url != null) displayText = extractDisplay(rawText);

        JTextPane textComp = new JTextPane();
        textComp.setEditable(false);
        textComp.setOpaque(false);
        textComp.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));

        // --- 1. Main Text Attribute (Uses Smart Font Logic for Language) ---
        SimpleAttributeSet mainAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(mainAttrs, ChatThemeUtils.getSmartFontName(displayText));
        StyleConstants.setFontSize(mainAttrs, 14);

        // --- 2. Emoji Attribute (Uses Emoji Font) ---
        SimpleAttributeSet emojiAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(emojiAttrs, "Segoe UI Emoji");
        StyleConstants.setFontSize(emojiAttrs, 14);

        // --- 3. Degree/Math Attribute (Uses Symbol Font - FIX FOR TOFU) ---
        SimpleAttributeSet degreeAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(degreeAttrs, "Segoe UI Symbol"); 
        StyleConstants.setFontSize(degreeAttrs, 14);

        if (isHeader || isBold) { 
            StyleConstants.setBold(mainAttrs, true); 
            StyleConstants.setBold(emojiAttrs, true); 
            StyleConstants.setBold(degreeAttrs, true);
        } else if (isItalic) { 
            StyleConstants.setItalic(mainAttrs, true); 
            StyleConstants.setItalic(emojiAttrs, true);
            StyleConstants.setItalic(degreeAttrs, true);
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
            StyleConstants.setForeground(mainAttrs, textColor); 
            StyleConstants.setForeground(emojiAttrs, textColor);
            StyleConstants.setForeground(degreeAttrs, textColor);
        }

        // Pass all 3 attribute sets to the parser
        parseContent(textComp, displayText, mainAttrs, emojiAttrs, degreeAttrs);
        return textComp;
    }

    private static void parseContent(JTextPane textComp, String text, SimpleAttributeSet mainAttrs, SimpleAttributeSet emojiAttrs, SimpleAttributeSet degreeAttrs) {
        try {
            StyledDocument doc = textComp.getStyledDocument();
            Matcher mathMatcher = MATH_PATTERN.matcher(text);
            int lastIdx = 0;
            while (mathMatcher.find()) {
                String preText = text.substring(lastIdx, mathMatcher.start());
                if (!preText.isEmpty()) insertWithFallback(doc, preText, mainAttrs, emojiAttrs, degreeAttrs);
                
                String mathRaw = mathMatcher.group();
                String mathContent = MathRenderer.extractMathContent(mathRaw);
                Icon icon = MathRenderer.latexToIcon(mathRaw, mathContent);
                if (icon != null) { textComp.setCaretPosition(doc.getLength()); textComp.insertIcon(icon); doc.insertString(doc.getLength(), " ", mainAttrs); }
                else { doc.insertString(doc.getLength(), mathRaw, mainAttrs); }
                lastIdx = mathMatcher.end();
            }
            if (lastIdx < text.length()) insertWithFallback(doc, text.substring(lastIdx), mainAttrs, emojiAttrs, degreeAttrs);
            doc.setParagraphAttributes(0, doc.getLength(), mainAttrs, false);
        } catch (BadLocationException e) { textComp.setText(text); }
    }

    private static void insertWithFallback(StyledDocument doc, String text, SimpleAttributeSet mainAttrs, SimpleAttributeSet emojiAttrs, SimpleAttributeSet degreeAttrs) throws BadLocationException {
        Matcher matcher = SPECIAL_CHARS_PATTERN.matcher(text);
        int lastIdx = 0;
        while (matcher.find()) {
            // Insert normal text (using Main Font)
            doc.insertString(doc.getLength(), text.substring(lastIdx, matcher.start()), mainAttrs);
            
            // Check which group matched
            if (matcher.group(1) != null) {
                // Group 1: Emoji -> Use Emoji Font
                doc.insertString(doc.getLength(), matcher.group(1), emojiAttrs);
            } else if (matcher.group(2) != null) {
                // Group 2: Degree -> Use Symbol Font (Fixes Tofu)
                doc.insertString(doc.getLength(), matcher.group(2), degreeAttrs);
            }
            
            lastIdx = matcher.end();
        }
        // Insert remaining text
        doc.insertString(doc.getLength(), text.substring(lastIdx), mainAttrs);
    }
    
    // Kept for fallback, though createCellComponent now explicitly sets fonts
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