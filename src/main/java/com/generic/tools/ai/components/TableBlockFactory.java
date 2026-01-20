package com.generic.tools.ai.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

public class TableBlockFactory {

    public static Component createTable(String markdownTable, Color parentBg) {
        JPanel tablePanel = new JPanel(new GridBagLayout());
        
        tablePanel.putClientProperty("isTableBlock", true); 
        
        Color borderColor = ChatThemeUtils.getTableBorderColor(parentBg);
        Color headerBg    = ChatThemeUtils.getTableHeaderColor(parentBg);
        Color cellBg      = ChatThemeUtils.getTableCellColor(parentBg);
        Color textColor   = ChatThemeUtils.getContrastingTextColor(cellBg);
        Color linkColor   = new Color(0, 102, 204); 

        tablePanel.setBackground(borderColor); 
        tablePanel.setBorder(BorderFactory.createLineBorder(borderColor, 1));

        String processedMarkdown = TableClipboardUtils.flattenLatex(markdownTable);
        String[] rows = processedMarkdown.split("\n");
        
        // 1. Calculate Max Columns
        int maxCols = 0;
        for (String line : rows) {
             if (line.trim().isEmpty() || line.replaceAll("[\\|\\-\\s:]", "").isEmpty()) continue;
             maxCols = Math.max(maxCols, smartSplit(line).size());
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(1, 1, 1, 1); 

        int row = 0;
        for (String line : rows) {
            if (line.trim().isEmpty()) continue;
            if (line.replaceAll("[\\|\\-\\s:]", "").isEmpty()) continue; 

            List<String> cells = smartSplit(line);
            
            int col = 0;
            for (String cellText : cells) {
                boolean isHeader = (row == 0);
                
                // Distribute width evenly, but allow expansion
                gbc.weightx = 1.0 / maxCols; 
                
                JTextPane textComp = TableCellRenderer.createCellComponent(
                        cellText.trim(), 
                        isHeader, 
                        textColor, 
                        linkColor
                );
                
                TableClipboardUtils.attachCopyMenu(textComp, processedMarkdown);

                JPanel cellPanel = new JPanel(new BorderLayout());
                cellPanel.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                
                cellPanel.putClientProperty("isTableHeader", isHeader);
                cellPanel.putClientProperty("isTableCell", !isHeader);
                cellPanel.setBackground(isHeader ? headerBg : cellBg);
                
                cellPanel.add(textComp, BorderLayout.CENTER);
                
                gbc.gridx = col;
                gbc.gridy = row;
                tablePanel.add(cellPanel, gbc);
                col++;
            }
            // Fill empty cells
            while(col < maxCols) {
                 JPanel empty = new JPanel();
                 empty.setBackground(row == 0 ? headerBg : cellBg);
                 gbc.gridx = col; gbc.gridy = row;
                 tablePanel.add(empty, gbc);
                 col++;
            }
            row++;
        }
        
        // --- SCROLLBAR LOGIC ---
        JScrollPane scrollPane = new JScrollPane(tablePanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        
        // Fix Mouse Wheel: Pass vertical scroll events to the parent Chat Panel
        // otherwise the mouse gets "stuck" when hovering over the table.
        scrollPane.addMouseWheelListener(e -> {
            if (!e.isShiftDown()) { // If not horizontal scrolling
                JScrollPane parent = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, scrollPane);
                if (parent != null) {
                    parent.dispatchEvent(e);
                }
            }
        });

        return scrollPane;
    }

    private static List<String> smartSplit(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inCode = false;
        boolean inMath = false;

        String trimmed = line.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        char[] chars = trimmed.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '`') inCode = !inCode;
            if (c == '$' && !inCode) inMath = !inMath;

            if (c == '|' && !inCode && !inMath) {
                cells.add(current.toString());
                current.setLength(0); 
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }
}