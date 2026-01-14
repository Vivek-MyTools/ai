package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class TableBlockFactory {

    private static final Pattern CELL_PATTERN = Pattern.compile(
        "\\*\\*(.*?)\\*\\*|" +                   // 1: Bold
        "\\*([^\\n*]+)\\*|" +                    // 2: Italic
        "<?(https?://[^\\s]+)>?|" +              // 3: Raw URL
        "\\[(.*?)\\]\\((https?://.*?)\\)"        // 4,5: Md Link
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
        
        int row = 0;
        
        for (String line : lines) {
            if (line.contains("---")) continue; 
            
            // Handle if line is just empty or whitespace (due to regex relaxation)
            if (line.trim().isEmpty()) continue;

            String[] cells = line.trim().split("\\|");
            int startIdx = (cells.length > 0 && cells[0].isEmpty()) ? 1 : 0;
            
            int col = 0;
            for (int i = startIdx; i < cells.length; i++) {
                String rawText = cells[i].trim();
                
                // --- 🧹 CLEANUP WEIRD CHARS ---
                rawText = rawText.replace("<br>", "\n").replace("<br/>", "\n");
                rawText = rawText.replace("\u2103", "°C"); 
                rawText = rawText.replace("\u2109", "°F"); 
                rawText = rawText.replace("\u00B0", "°");
                
                // 🛑 Fix Tofu: Replace Smart Hyphens/Dashes with standard dash
                rawText = rawText.replace("\u2010", "-"); // Hyphen
                rawText = rawText.replace("\u2011", "-"); // Non-breaking Hyphen
                rawText = rawText.replace("\u2012", "-"); // Figure Dash
                rawText = rawText.replace("\u2013", "-"); // En Dash
                rawText = rawText.replace("\u2014", "-"); // Em Dash
                rawText = rawText.replace("\u2015", "-"); // Horizontal Bar
                rawText = rawText.replace("\u2212", "-"); // Minus Sign
                rawText = rawText.replace("\u00A0", " "); // Non-breaking Space

                JPanel cellPanel = new JPanel(new java.awt.BorderLayout());
                cellPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                
                boolean isHeader = (row == 0);
                cellPanel.putClientProperty("isTableHeader", isHeader);
                cellPanel.putClientProperty("isTableCell", !isHeader);
                cellPanel.setBackground(isHeader ? headerBg : cellBg);

                // 🔧 FIX: Custom JTextPane to handle sizing correctly
                JTextPane textComp = new JTextPane() {
                    @Override
                    public Dimension getPreferredSize() {
                        Dimension d = super.getPreferredSize();
                        // Report 0 width so layout manager doesn't expand infinitely (Prevents Flicker)
                        // Report ACTUAL height so text isn't hidden (Prevents Collapse)
                        return new Dimension(0, d.height);
                    }
                };
                
                textComp.setOpaque(false);
                textComp.setEditable(false);
                textComp.putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

                Font font = new Font(Font.SANS_SERIF, 
                                     isHeader ? Font.BOLD : Font.PLAIN, 14);
                textComp.setFont(font);
                textComp.setForeground(textColor);

                // 🔹 PARSE INSIDE CELL
                miniParse(textComp, rawText, font, linkColor, textColor);

                cellPanel.add(textComp, java.awt.BorderLayout.CENTER);
                
                gbc.gridx = col;
                gbc.gridy = row;
                tablePanel.add(cellPanel, gbc);
                col++;
            }
            row++;
        }
        
        return tablePanel;
    }

    private static void miniParse(JTextPane pane, String text, Font baseFont, Color linkColor, Color textColor) {
        StyledDocument doc = pane.getStyledDocument();
        
        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setFontFamily(normal, baseFont.getFamily());
        StyleConstants.setFontSize(normal, baseFont.getSize());
        StyleConstants.setBold(normal, baseFont.isBold());
        StyleConstants.setForeground(normal, textColor);
        
        SimpleAttributeSet bold = new SimpleAttributeSet(normal);
        StyleConstants.setBold(bold, true);
        
        SimpleAttributeSet italic = new SimpleAttributeSet(normal);
        StyleConstants.setItalic(italic, true);
        
        SimpleAttributeSet link = new SimpleAttributeSet(normal);
        StyleConstants.setForeground(link, linkColor);
        StyleConstants.setUnderline(link, true);

        Matcher m = CELL_PATTERN.matcher(text);
        int lastIndex = 0;
        
        try {
            while (m.find()) {
                String preText = text.substring(lastIndex, m.start());
                if (!preText.isEmpty()) doc.insertString(doc.getLength(), preText, normal);
                
                if (m.group(1) != null) doc.insertString(doc.getLength(), m.group(1), bold);
                else if (m.group(2) != null) doc.insertString(doc.getLength(), m.group(2), italic);
                else if (m.group(3) != null) {
                    String url = m.group(3);
                    doc.insertString(doc.getLength(), url, link);
                    setupCellLink(pane, url);
                }
                else if (m.group(4) != null && m.group(5) != null) {
                    String title = m.group(4);
                    String url = m.group(5);
                    doc.insertString(doc.getLength(), title, link);
                    setupCellLink(pane, url);
                }
                
                lastIndex = m.end();
            }
            
            if (lastIndex < text.length()) {
                doc.insertString(doc.getLength(), text.substring(lastIndex), normal);
            }
        } catch (BadLocationException ignored) {}
    }

    private static void setupCellLink(JTextPane pane, String url) {
        pane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    ChatThemeUtils.openBrowser(url);
                }
            }
        });
    }
}