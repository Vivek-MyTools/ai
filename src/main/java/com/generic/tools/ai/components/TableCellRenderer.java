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
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class TableCellRenderer {

    // Regex for Markdown links [text](url) or raw URLs
    private static final Pattern LINK_PATTERN = Pattern.compile("<(https?://[^>\\s]+)>|\\[(.*?)\\]\\((https?://.*?)\\)");
    
    // MASTER REGEX: Matches Code (`...`) OR Math ($$, \(, \[, $)
    private static final Pattern CELL_MASTER_PATTERN = Pattern.compile(
        "(`([^`]+)`)|" + 
        "((\\$\\$(?s:.*?)\\$\\$|\\\\\\((?s:.*?)\\\\\\)|\\\\\\[(?s:.*?)\\\\\\]|\\$(?!\\{)([^$]+?)\\$))" 
    );

    public static JTextPane createCellComponent(String rawText, boolean isHeader, Color textColor, Color linkColor) {
        // 1. Basic Sanitization
        if (rawText == null) rawText = "";
        rawText = rawText.replace("\u00A0", " ").replace("\uFE0F", "");
        rawText = rawText.replace("<br>", "\n").replace("<br/>", "\n").trim();
        
        // 2. Handle Markdown Bold/Italic
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
        
        // FIX: Use the smart Unicode font, NOT Dialog.
        // This matches the Main Chat behavior.
        textComp.setFont(new Font(ChatThemeUtils.getUnicodeFontName(), Font.PLAIN, 14));
        
        textComp.putClientProperty("isTableHeader", isHeader);

        // --- ATTRIBUTES SETUP ---
        SimpleAttributeSet mainAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(mainAttrs, ChatThemeUtils.getUnicodeFontName());
        StyleConstants.setFontSize(mainAttrs, 14);

        SimpleAttributeSet codeAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(codeAttrs, "Monospaced");
        StyleConstants.setFontSize(codeAttrs, 14);
        StyleConstants.setBackground(codeAttrs, new Color(128, 128, 128, 40)); 

        if (isHeader || isBold) { 
            StyleConstants.setBold(mainAttrs, true); StyleConstants.setBold(codeAttrs, true);
        } else if (isItalic) { 
            StyleConstants.setItalic(mainAttrs, true); StyleConstants.setItalic(codeAttrs, true);
        }

        if (url != null) {
            Color c = linkColor;
            StyleConstants.setForeground(mainAttrs, c); 
            StyleConstants.setUnderline(mainAttrs, true); 
            textComp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            
            final String targetUrl = url;
            textComp.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { 
                    if (SwingUtilities.isLeftMouseButton(e)) ChatThemeUtils.openBrowser(targetUrl); 
                }
            });
        } else {
            Color c = textColor;
            StyleConstants.setForeground(mainAttrs, c); 
            StyleConstants.setForeground(codeAttrs, c); 
        }       
        
        textComp.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int pos = textComp.viewToModel2D(e.getPoint());
                if (pos >= 0) {
                    StyledDocument doc = textComp.getStyledDocument();
                    AttributeSet attr = doc.getCharacterElement(pos).getAttributes();

                    // Check if the character has the "linkURL" attribute we set during parsing
                    if (attr.getAttribute("linkURL") != null) {
                        textComp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        return;
                    }
                }
                textComp.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        });
        
        parseContent(textComp, displayText, mainAttrs, codeAttrs);
        return textComp;
    }

    
    private static String getUrlAtPoint(JTextPane textComp, java.awt.Point pt) {
        int pos = textComp.viewToModel2D(pt);
        if (pos >= 0) {
            StyledDocument doc = textComp.getStyledDocument();
            AttributeSet attr = doc.getCharacterElement(pos).getAttributes();
            return (String) attr.getAttribute("linkURL");
        }
        return null;
    }
    
    private static void parseContent(JTextPane textComp, String text, SimpleAttributeSet mainAttrs, SimpleAttributeSet codeAttrs) {
        try {
            StyledDocument doc = textComp.getStyledDocument();
            Matcher matcher = CELL_MASTER_PATTERN.matcher(text);
            int lastIdx = 0;
            
            while (matcher.find()) {
                // Insert Regular Text (Uses Updated Smart Insert)
                String preText = text.substring(lastIdx, matcher.start());
                if (!preText.isEmpty()) {
                    ChatThemeUtils.smartInsert(doc, preText, mainAttrs);
                }
                
                // Group 1: Code (`...`)
                if (matcher.group(1) != null) {
                    String codeContent = matcher.group(2); 
                    doc.insertString(doc.getLength(), codeContent, codeAttrs);
                } 
                // Group 3: Math ($...$ etc)
                else if (matcher.group(3) != null)  {
                    String mathRaw = matcher.group(); 
                    String mathContent = MathRenderer.extractMathContent(mathRaw);
                    
                    Icon icon = MathRenderer.latexToIcon(mathRaw, mathContent, StyleConstants.getForeground(mainAttrs));
                    
                    if (icon != null) { 
                        SimpleAttributeSet iconAttrs = new SimpleAttributeSet(mainAttrs); 
                        StyleConstants.setIcon(iconAttrs, icon);
                        iconAttrs.addAttribute("LatexRaw", mathRaw);
                        iconAttrs.addAttribute("LatexContent", mathContent);
                        doc.insertString(doc.getLength(), " ", iconAttrs); 
                    } else { 
                        // Fallback: Smart insert the raw math text
                        ChatThemeUtils.smartInsert(doc, mathRaw, mainAttrs); 
                    }
                } else {
                    String display = null; String url = null;
                    if (matcher.group(6) != null) { url = matcher.group(7); display = url; }
                    else if (matcher.group(8) != null) { display = matcher.group(9); url = matcher.group(10); }
                    else if (matcher.group(11) != null) { url = matcher.group(11); display = url; }

                    if (url != null) {
                        SimpleAttributeSet linkAttrs = new SimpleAttributeSet(mainAttrs);
                        StyleConstants.setForeground(linkAttrs, Color.BLUE);
                        StyleConstants.setUnderline(linkAttrs, true);
                        linkAttrs.addAttribute("linkURL", url);

                        // FIXED: Use smartInsert for display text to handle emojis/icons inside links
                        ChatThemeUtils.smartInsert(doc, display, linkAttrs);
                    }
                }
                lastIdx = matcher.end();
            }
            
            // Insert remaining text
            if (lastIdx < text.length()) {
                 ChatThemeUtils.smartInsert(doc, text.substring(lastIdx), mainAttrs);
            }
            
            doc.setParagraphAttributes(0, doc.getLength(), mainAttrs, false);
            
        } catch (BadLocationException e) { 
            textComp.setText(text); 
        }
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