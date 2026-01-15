package com.generic.tools.ai.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextPane;

/**
 * Main Factory to create Table blocks.
 * Refactored to delegate logic to TableClipboardUtils and TableCellRenderer.
 */
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

        // 1. Flatten LaTeX (handled by Utils now)
        String processedMarkdown = TableClipboardUtils.flattenLatex(markdownTable.trim());

        // 2. Attach Context Menu (handled by Utils now)
        TableClipboardUtils.attachCopyMenu(tablePanel, processedMarkdown);

        String[] lines = processedMarkdown.split("\\n");
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(1, 1, 0, 0); 
        gbc.weightx = 1.0; 
        gbc.weighty = 0.0; 
        
        int row = 0;
        
        for (String line : lines) {
            if (line.contains("---")) continue; 
            
            String[] cells = line.trim().split("\\|");
            int startIdx = (cells.length > 0 && cells[0].isEmpty()) ? 1 : 0;
            
            int col = 0;
            for (int i = startIdx; i < cells.length; i++) {
                
                // 3. Delegate UI Creation to CellRenderer
                boolean isHeader = (row == 0);
                JTextPane textComp = TableCellRenderer.createCellComponent(
                        cells[i].trim(), 
                        isHeader, 
                        textColor, 
                        linkColor
                );
                
                // Attach context menu to individual cells too
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
}