package com.generic.tools.ai.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextPane;

public class TableBlockFactory {

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

        String processedMarkdown = TableClipboardUtils.flattenLatex(markdownTable);
        String[] rows = processedMarkdown.split("\n");
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(1, 1, 1, 1); // Grid gap

        int row = 0;
        for (String line : rows) {
            if (line.trim().isEmpty()) continue;
            // Skip markdown separator lines like |---|---|
            if (line.replaceAll("[\\|\\-\\s:]", "").isEmpty()) continue; 

            // FIX: Use smartSplit instead of string.split("|")
            // This prevents LaTeX formulas like $P(A|B)$ from being cut in half.
            List<String> cells = smartSplit(line);
            
            int col = 0;
            for (String cellText : cells) {
                boolean isHeader = (row == 0);
                
                // Render Cell (Code + Math Support)
                JTextPane textComp = TableCellRenderer.createCellComponent(
                        cellText.trim(), 
                        isHeader, 
                        textColor, 
                        linkColor
                );
                
                TableClipboardUtils.attachCopyMenu(textComp, processedMarkdown);

                JPanel cellPanel = new JPanel(new BorderLayout());
                cellPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                
                cellPanel.putClientProperty("isTableHeader", isHeader);
                cellPanel.putClientProperty("isTableCell", !isHeader);
                cellPanel.setBackground(isHeader ? headerBg : cellBg);
                
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

    // --- FIX: Smart Splitter ---
    // Splits by '|' ONLY if not inside Code (`) or Math ($) delimiters.
    private static List<String> smartSplit(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inCode = false;
        boolean inMath = false;

        // Trim outer pipes first: "| A | B |" -> " A | B "
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        char[] chars = trimmed.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            
            // Toggle states (ignoring escaped chars)
            if (c == '`') inCode = !inCode;
            if (c == '$' && !inCode) inMath = !inMath;

            // Split on Pipe if SAFE
            if (c == '|' && !inCode && !inMath) {
                cells.add(current.toString());
                current.setLength(0); // Reset buffer
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString()); // Add last cell
        return cells;
    }
}