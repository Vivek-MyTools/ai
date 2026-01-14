package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.SystemColor;
import java.net.URI;
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
    public static Color getUserBubbleColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        
        // LOGIC: Stronger contrast than the Agent
        // Light Theme: Make it significantly darker (0.85 factor)
        // Dark Theme: Make it significantly lighter (1.30 factor)
        float factor = isDark ? 1.30f : 0.85f; 
        
        int r = Math.min(255, (int) (baseBg.getRed() * factor));
        int g = Math.min(255, (int) (baseBg.getGreen() * factor));
        int b = Math.min(255, (int) (baseBg.getBlue() * factor));
        
        return new Color(r, g, b);
    }
    
    public static Color getAgentBubbleColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        
        // LOGIC: 
        // If Light Theme: Darken the background (Factor < 1.0)
        // If Dark Theme: Lighten the background (Factor > 1.0) to create "Surface" effect
        float factor = isDark ? 1.12f : 0.94f; // 6-12% difference is subtle but visible
        
        int r = Math.min(255, (int) (baseBg.getRed() * factor));
        int g = Math.min(255, (int) (baseBg.getGreen() * factor));
        int b = Math.min(255, (int) (baseBg.getBlue() * factor));
        
        return new Color(r, g, b);
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
    
    public static Color getTableBorderColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        // Dark Mode: 40% Lighter than bg (visible border)
        // Light Mode: 15% Darker than bg
        float factor = isDark ? 1.40f : 0.85f; 
        return shiftColor(baseBg, factor);
    }

    public static Color getTableHeaderColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        // Header: 20% Lighter than bg (Dark Mode) or 5% Darker (Light Mode)
        float factor = isDark ? 1.20f : 0.95f;
        return shiftColor(baseBg, factor);
    }

    public static Color getTableCellColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        // Cell: 5% Lighter than bg (Dark Mode) to stay subtle
        // Light Mode: White (clean look)
        if (isDark) {
            return shiftColor(baseBg, 1.05f); 
        } else {
            return Color.WHITE;
        }
    }

    // 🎨 MATH HELPER: Lightens or Darkens a color while keeping its Hue
    private static Color shiftColor(Color c, float factor) {
        int r = Math.min(255, Math.max(0, (int) (c.getRed() * factor)));
        int g = Math.min(255, Math.max(0, (int) (c.getGreen() * factor)));
        int b = Math.min(255, Math.max(0, (int) (c.getBlue() * factor)));
        return new Color(r, g, b);
    }
    
    public static void openBrowser(String url) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                // Handle potential trailing punctuation from markdown like ")" or "."
                String cleanUrl = url;
                if (cleanUrl.endsWith(")") || cleanUrl.endsWith(".") || cleanUrl.endsWith(",")) {
                    cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
                }
                Desktop.getDesktop().browse(new URI(cleanUrl));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}