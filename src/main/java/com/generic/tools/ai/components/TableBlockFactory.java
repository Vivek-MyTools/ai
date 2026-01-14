package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class TableBlockFactory {

    public static JPanel createTable(String markdownTable, Color parentBg) {
        JPanel tablePanel = new JPanel(new GridBagLayout());
        boolean isDark = ChatThemeUtils.isDarkTheme(parentBg);
        
        // Colors
        Color borderColor = isDark ? new Color(80, 80, 80) : new Color(200, 200, 200);
        Color headerBg    = isDark ? new Color(60, 60, 60) : new Color(230, 230, 230);
        Color cellBg      = isDark ? new Color(40, 44, 52) : Color.WHITE;
        Color textColor   = isDark ? Color.WHITE : Color.BLACK;

        tablePanel.setBackground(borderColor); // The panel bg acts as border lines
        tablePanel.setBorder(BorderFactory.createLineBorder(borderColor, 1));

        String[] lines = markdownTable.trim().split("\\n");
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(1, 1, 0, 0); // 1px gap for grid effect
        gbc.weightx = 1.0;
        
        int row = 0;
        
        for (String line : lines) {
            // Skip separator lines like |---|
            if (line.contains("---")) continue; 
            
            String[] cells = line.trim().split("\\|");
            
            // Handle if line starts with | (split gives empty first string)
            int startIdx = (cells.length > 0 && cells[0].isEmpty()) ? 1 : 0;
            
            int col = 0;
            for (int i = startIdx; i < cells.length; i++) {
                String cellText = cells[i].trim();
                cellText = cellText.replace("<br>", "\n").replace("<br/>", "\n");

                JPanel cellPanel = new JPanel(new java.awt.BorderLayout());
                cellPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                
                boolean isHeader = (row == 0);
                cellPanel.setBackground(isHeader ? headerBg : cellBg);

                JTextArea textComp = new JTextArea(cellText);
                textComp.setWrapStyleWord(true);
                textComp.setLineWrap(true);
                textComp.setOpaque(false);
                textComp.setEditable(false);
                textComp.setForeground(textColor);
                
                textComp.setFont(new Font(ChatThemeUtils.getUnicodeFontName(), 
                                          isHeader ? Font.BOLD : Font.PLAIN, 14));

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