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

    /**
     * Creates a bubble. 
     * @param isUser true = Left Alignment (User), false = Right Alignment (Agent)
     */
    public static JPanel createBubble(String text, Color parentBg, Icon icon, boolean isUser) {
        // 1. Wrapper
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        // Tag appropriately for theme updates
        wrapper.putClientProperty("isUserBubble", isUser);
        wrapper.putClientProperty("isAgentBubble", !isUser);
        
        GridBagConstraints gbc = new GridBagConstraints();
        
        // --- LAYOUT LOGIC ---
        // IF USER (Left): Icon -> Bubble -> Empty Space
        // IF AGENT (Right): Empty Space -> Bubble -> Icon (Optional)
        int iconCol   = isUser ? 2 : 0;
        int bubbleCol = 1;
        int spaceCol  = isUser ? 0 : 2;
        
        // 2. The Icon
        if (icon != null) {
            JLabel iconLabel = new JLabel(icon);
            gbc.gridx = iconCol;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            // Add padding between icon and bubble
            gbc.insets = isUser ? new Insets(0, 8, 0, 8) : new Insets(0, 8, 0, 8); 
            wrapper.add(iconLabel, gbc);
        }

        // 3. The Bubble
        JPanel bubble = new RoundedPanel();
        bubble.setLayout(new BorderLayout());
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JTextArea textArea = new JTextArea(text);
        textArea.setOpaque(false);
        textArea.setFont(new java.awt.Font(ChatThemeUtils.getUnicodeFontName(), java.awt.Font.PLAIN, 14));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        
        // Dynamic Size
        int desiredWidth = Math.min(350, Math.max(100, text.length() * 8));
        textArea.setSize(new java.awt.Dimension(desiredWidth, Short.MAX_VALUE)); 
        int columns = Math.min(50, text.length()); 
        textArea.setColumns(columns > 0 ? columns : 1);

        bubble.add(textArea, BorderLayout.CENTER);

        // Bubble Constraints
        gbc.gridx = bubbleCol;
        gbc.gridy = 0;
        gbc.weightx = 0; // Do not expand width infinitely, let the "spacer" do it
        
        // Align bubble to Left (West) or Right (East) based on who spoke
        gbc.anchor = isUser ? GridBagConstraints.WEST : GridBagConstraints.EAST;
        
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(0, 0, 0, 0); 
        wrapper.add(bubble, gbc);
        
        // 4. Spacer (Pushes content to the correct side)
        // If User, spacer is on Right (pushes User to Left)
        // If Agent, spacer is on Left (pushes Agent to Right)
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        gbc.gridx = spaceCol;
        gbc.gridy = 0;
        gbc.weightx = 1.0; // Consumes all extra space
        gbc.fill = GridBagConstraints.HORIZONTAL;
        wrapper.add(spacer, gbc);
        
        // 5. Apply Theme
        applyTheme(wrapper, parentBg);
        
        return wrapper;
    }
    
    // 🔄 RE-THEMING LOGIC
    public static void applyTheme(JPanel wrapper, Color parentBg) {
        boolean isUser = Boolean.TRUE.equals(wrapper.getClientProperty("isUserBubble"));
        
        for (java.awt.Component comp : wrapper.getComponents()) {
            if (comp instanceof RoundedPanel) {
                JPanel bubble = (JPanel) comp;
                
                // Choose color based on type
                Color bubbleColor;
                if (isUser) {
                    // 👈 UPDATED: Now passes parentBg
                    bubbleColor = ChatThemeUtils.getUserBubbleColor(parentBg); 
                } else {
                    bubbleColor = ChatThemeUtils.getAgentBubbleColor(parentBg); 
                }
                
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
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
        }
    }
}