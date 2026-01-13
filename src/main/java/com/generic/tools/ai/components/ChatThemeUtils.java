package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.SystemColor;
import javax.swing.UIManager;

public class ChatThemeUtils {

    // 1. Get the Main Background from the Look and Feel
    public static Color getCanvasBackground() {
        Color c = UIManager.getColor("TextArea.background");
        return (c != null) ? c : Color.WHITE; 
    }

    // 2. Get the Main Text Color from the Look and Feel
    public static Color getCanvasForeground() {
        Color c = UIManager.getColor("TextArea.foreground");
        return (c != null) ? c : Color.BLACK;
    }
    
    // 3. 🎨 NEW: Calculate Bubble Color relative to Parent Background
    // This creates the "slightly darker" or "elevated" look you requested
    public static Color getBubbleBackgroundColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        
        // Logic: 
        // Light Theme: Darken the background slightly (0.95 factor)
        // Dark Theme: Lighten the background slightly (1.15 factor) to create "elevation"
        float factor = isDark ? 1.15f : 0.95f;
        
        int r = Math.min(255, (int) (baseBg.getRed() * factor));
        int g = Math.min(255, (int) (baseBg.getGreen() * factor));
        int b = Math.min(255, (int) (baseBg.getBlue() * factor));
        
        return new Color(r, g, b);
    }

    // 4. Get a "Native" looking Highlight Color (fallback if needed)
    public static Color getUserBubbleColor() {
        Color c = UIManager.getColor("Component.accentColor");
        if (c == null) c = SystemColor.textHighlight;
        if (c == null) c = new Color(0, 120, 215); 
        return c;
    }

    // 5. Calculate readable text color
    public static Color getContrastingTextColor(Color bg) {
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return (brightness < 0.6) ? Color.WHITE : Color.BLACK;
    }
    
    public static String getUnicodeFontName() {
         String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String fontName : fontNames) {
            if (fontName.equalsIgnoreCase("Nirmala UI")) return "Nirmala UI";
        }
        return "Dialog";
    }

    public static boolean isDarkTheme(Color bg) {
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return brightness < 0.5;
    }
    
    public static Color getCodeBackgroundColor(Color baseBg) {
        // We can reuse the bubble logic or keep it distinct. 
        // Using the same logic ensures consistency.
        return getBubbleBackgroundColor(baseBg);
    }
}