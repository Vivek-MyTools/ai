package com.generic.tools.ai.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class TableBlockFactory {

    private static final Pattern LINK_PATTERN = Pattern.compile(
        "<?(https?://[^\\s]+)>?|" +              
        "\\[(.*?)\\]\\((https?://.*?)\\)"       
    );

    // 1. New Math Pattern (Matches $$...$$, \[...\], \(...\), $...$)
    private static final Pattern MATH_PATTERN = Pattern.compile(
            "(\\$\\$(?s:.*?)\\$\\$|\\\\\\((?s:.*?)\\\\\\)|\\\\\\[(?s:.*?)\\\\\\]|\\$(?!\\{)([^$]+?)\\$)"
    );

    public static JPanel createTable(String markdownTable, Color parentBg) {
        JPanel tablePanel = new JPanel(new GridBagLayout());
        
        tablePanel.putClientProperty("isTableBlock", true); 
        
        Color borderColor = ChatThemeUtils.getTableBorderColor(parentBg);
        Color headerBg    = ChatThemeUtils.getTableHeaderColor(parentBg);
        Color cellBg      = ChatThemeUtils.getTableCellColor(parentBg);
        Color textColor   = ChatThemeUtils.getContrastingTextColor(cellBg);
        Color linkColor   = new Color(0, 102, 204); 

        tablePanel.setBackground(borderColor); 
        tablePanel.setBorder(BorderFactory.createLineBorder(borderColor, 1));

        String[] lines = markdownTable.trim().split("\\n");
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(1, 1, 0, 0); 
        gbc.weightx = 1.0; 
        gbc.weighty = 0.0; 
        
        // Font for Indian Languages / text
        String safeFontName = ChatThemeUtils.getUnicodeFontName();
        
        int row = 0;
        
        for (String line : lines) {
            if (line.contains("---")) continue; 
            
            String[] cells = line.trim().split("\\|");
            int startIdx = (cells.length > 0 && cells[0].isEmpty()) ? 1 : 0;
            
            int col = 0;
            for (int i = startIdx; i < cells.length; i++) {
                String rawText = cells[i].trim();
                
                // --- 🧹 CLEANUP & FORMATTING ---
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

                rawText = rawText.replace("\u2103", "°C"); 
                rawText = rawText.replace("\u2109", "°F"); 
                rawText = rawText.replace("\u00B0", "°");

                JPanel cellPanel = new JPanel(new BorderLayout());
                cellPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                
                boolean isHeader = (row == 0);
                cellPanel.putClientProperty("isTableHeader", isHeader);
                cellPanel.putClientProperty("isTableCell", !isHeader);
                cellPanel.setBackground(isHeader ? headerBg : cellBg);

                // Link Parsing
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

                JTextPane textComp = new JTextPane();
                textComp.setEditable(false);
                textComp.setOpaque(false);
                
                // Base Font (Dialog) for Emoji fallback [Preserved Fix]
                textComp.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
                
                // Attributes for Specific Text Font (e.g. Nirmala UI)
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setFontFamily(attrs, safeFontName);
                StyleConstants.setFontSize(attrs, 14);
                
                if (isHeader || isBold) {
                    StyleConstants.setBold(attrs, true);
                } else if (isItalic) {
                    StyleConstants.setItalic(attrs, true);
                }
                
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

                // --- 2. PARSE TEXT + MATH LOOP ---
                try {
                    StyledDocument doc = textComp.getStyledDocument();
                    
                    Matcher mathMatcher = MATH_PATTERN.matcher(displayText);
                    int lastIdx = 0;
                    
                    while (mathMatcher.find()) {
                        // A. Insert plain text before the math
                        String preText = displayText.substring(lastIdx, mathMatcher.start());
                        if (!preText.isEmpty()) {
                            doc.insertString(doc.getLength(), preText, attrs);
                        }
                        
                        // B. Render Math to Icon
                        String mathRaw = mathMatcher.group();
                        String mathContent = MathRenderer.extractMathContent(mathRaw); // Uses existing Helper
                        Icon icon = MathRenderer.latexToIcon(mathRaw, mathContent);
                        
                        if (icon != null) {
                            // Important: Move caret to end before inserting icon
                            textComp.setCaretPosition(doc.getLength());
                            textComp.insertIcon(icon);
                            
                            // Insert a spacer space after icon for readability (optional)
                            doc.insertString(doc.getLength(), " ", attrs); 
                        } else {
                            // Fallback: If render fails, show raw LaTeX
                            doc.insertString(doc.getLength(), mathRaw, attrs);
                        }
                        
                        lastIdx = mathMatcher.end();
                    }
                    
                    // C. Insert remaining text after last math block
                    if (lastIdx < displayText.length()) {
                        doc.insertString(doc.getLength(), displayText.substring(lastIdx), attrs);
                    }
                    
                    // Apply Paragraph Attributes for alignment
                    doc.setParagraphAttributes(0, doc.getLength(), attrs, false);

                } catch (BadLocationException ex) {
                    textComp.setText(displayText); // Fallback
                }

                cellPanel.add(textComp, BorderLayout.CENTER);
                
                gbc.gridx = col;
                gbc.gridy = row;
                tablePanel.add(cellPanel, gbc);
                col++;
            }
            row++;
        }
        
        return tablePanel;
    }
}