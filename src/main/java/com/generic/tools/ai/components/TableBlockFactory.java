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
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class TableBlockFactory {

    private static final Pattern LINK_PATTERN = Pattern.compile(
        "<?(https?://[^\\s]+)>?|" +              
        "\\[(.*?)\\]\\((https?://.*?)\\)"       
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
        // 🔧 FIX: Ensure row height can expand
        gbc.weighty = 0.0; 
        
        int row = 0;
        
        for (String line : lines) {
            if (line.contains("---")) continue; 
            
            String[] cells = line.trim().split("\\|");
            int startIdx = (cells.length > 0 && cells[0].isEmpty()) ? 1 : 0;
            
            int col = 0;
            for (int i = startIdx; i < cells.length; i++) {
                String rawText = cells[i].trim();
                
                // Cleanup
                rawText = rawText.replace("<br>", "\n").replace("<br/>", "\n");
                rawText = rawText.replace("\u2103", "°C"); 
                rawText = rawText.replace("\u2109", "°F"); 

                JPanel cellPanel = new JPanel(new java.awt.BorderLayout());
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

                JTextArea textComp = new JTextArea(displayText);
                textComp.setWrapStyleWord(true);
                textComp.setLineWrap(true);
                textComp.setOpaque(false);
                textComp.setEditable(false);
                
                // 🔧 FIX FLICKER + HEIGHT ISSUE:
                // 1. setColumns(0) stops it from requesting huge width (stops flicker).
                // 2. setRows(0) allows it to calculate natural height.
                // 3. setMinimumSize allows it to shrink if needed.
                // 4. REMOVED setPreferredSize to allow height to grow.
                textComp.setColumns(0);
                textComp.setRows(0);
                textComp.setMinimumSize(new Dimension(10, 10));
                
                Font font = new Font(ChatThemeUtils.getUnicodeFontName(), 
                                     isHeader ? Font.BOLD : Font.PLAIN, 14);

                if (url != null) {
                    textComp.setForeground(linkColor);
                    textComp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    
                    Map<TextAttribute, Object> attributes = new HashMap<>(font.getAttributes());
                    attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                    textComp.setFont(font.deriveFont(attributes));
                    
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
                    textComp.setForeground(textColor);
                    textComp.setFont(font);
                }

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
}