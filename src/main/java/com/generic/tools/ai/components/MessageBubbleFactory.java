package com.generic.tools.ai.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class MessageBubbleFactory {

    public static JPanel createUserBubble(String text, Color parentBg, Icon userIcon) {
        // 1. Wrapper uses GridBagLayout
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.putClientProperty("isUserBubble", true); 
        
        GridBagConstraints gbc = new GridBagConstraints();
        
        // 2. The Icon (Left Side - CENTERED VERTICALLY)
        if (userIcon != null) {
            JLabel iconLabel = new JLabel(userIcon);
            gbc.gridx = 0;
            gbc.gridy = 0;
            
            // ⚓ CENTER VERTICALLY relative to the bubble height
            gbc.anchor = GridBagConstraints.CENTER; 
            
            gbc.insets = new Insets(0, 0, 0, 8); // Right margin only
            wrapper.add(iconLabel, gbc);
        }

        // 3. The Bubble (Right Side)
        JPanel bubble = new RoundedPanel();
        bubble.setLayout(new BorderLayout());
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JTextArea textArea = new JTextArea(text);
        textArea.setOpaque(false);
        textArea.setFont(new java.awt.Font(ChatThemeUtils.getUnicodeFontName(), java.awt.Font.PLAIN, 14));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        
        // Sizing logic
        int desiredWidth = Math.min(350, Math.max(100, text.length() * 8));
        textArea.setSize(new java.awt.Dimension(desiredWidth, Short.MAX_VALUE)); 
        int columns = Math.min(50, text.length()); 
        textArea.setColumns(columns > 0 ? columns : 1);

        bubble.add(textArea, BorderLayout.CENTER);

        // Bubble Constraints
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0; 
        
        // ⚓ CENTER VERTICALLY ensures the row height respects the bubble, 
        // and the icon stays in the middle of that row.
        gbc.anchor = GridBagConstraints.WEST; 
        gbc.fill = GridBagConstraints.VERTICAL; // Ensure cell fills height so centering works
        
        gbc.insets = new Insets(0, 0, 0, 0); 
        wrapper.add(bubble, gbc);
        
        // 4. Apply Initial Theme
        applyTheme(wrapper, parentBg);
        
        return wrapper;
    }
    
    // 🔄 RE-THEMING LOGIC
    public static void applyTheme(JPanel wrapper, Color parentBg) {
        for (java.awt.Component comp : wrapper.getComponents()) {
            if (comp instanceof RoundedPanel) {
                JPanel bubble = (JPanel) comp;
                
                Color bubbleColor = ChatThemeUtils.getBubbleBackgroundColor(parentBg);
                bubble.setBackground(bubbleColor);
                
                Color textColor = ChatThemeUtils.getContrastingTextColor(bubbleColor);
                
                if (bubble.getComponentCount() > 0 && bubble.getComponent(0) instanceof JTextArea) {
                    ((JTextArea) bubble.getComponent(0)).setForeground(textColor);
                }
            }
        }
    }

    private static class RoundedPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.dispose();
        }
    }
}