package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.UIManager;

public class ChatThemeUtils {

    // --- NEW: Smart Font Detection ---
    // Checks if a specific text (like "Bengaluru" or "25°C") renders cleanly 
    // in our preferred fonts. Returns the best match for THAT specific string.
    public static String getSmartFontName(String text) {
        if (text == null || text.trim().isEmpty()) return getUnicodeFontName();

        // Priority List:
        // 1. Nirmala UI (Best for Kannada/Indic + English)
        // 2. Segoe UI Symbol (Best for Math/Weather symbols, but NO Kannada)
        // 3. Arial Unicode MS (Heavy fallback)
        // 4. Segoe UI Emoji (Last resort for colored emojis)
        String[] priorities = { "Nirmala UI", "Segoe UI Symbol", "Arial Unicode MS", "Segoe UI Emoji", Font.SANS_SERIF };
        
        for (String fontName : priorities) {
            Font f = new Font(fontName, Font.PLAIN, 14);
            // canDisplayUpTo returns -1 if the font can display the WHOLE string.
            if (f.canDisplayUpTo(text) == -1) {
                return fontName;
            }
        }
        
        // If no single font can display EVERYTHING (e.g. mixed Kannada + weird Math),
        // we default to the Unicode font (Nirmala) because reading Text is more important than a symbol.
        return getUnicodeFontName();
    }

    public static Color getCanvasBackground() {
        Color c = UIManager.getColor("TextArea.background");
        return (c != null) ? c : Color.WHITE; 
    }

    public static Color getCanvasForeground() {
        Color c = UIManager.getColor("TextArea.foreground");
        return (c != null) ? c : Color.BLACK;
    }
    
    public static Color getBubbleBackgroundColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        float factor = isDark ? 1.15f : 0.95f;
        int r = Math.min(255, (int) (baseBg.getRed() * factor));
        int g = Math.min(255, (int) (baseBg.getGreen() * factor));
        int b = Math.min(255, (int) (baseBg.getBlue() * factor));
        return new Color(r, g, b);
    }

    public static Color getUserBubbleColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        float factor = isDark ? 1.30f : 0.85f; 
        int r = Math.min(255, (int) (baseBg.getRed() * factor));
        int g = Math.min(255, (int) (baseBg.getGreen() * factor));
        int b = Math.min(255, (int) (baseBg.getBlue() * factor));
        return new Color(r, g, b);
    }
    
    public static Color getAgentBubbleColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        float factor = isDark ? 1.12f : 0.94f; 
        int r = Math.min(255, (int) (baseBg.getRed() * factor));
        int g = Math.min(255, (int) (baseBg.getGreen() * factor));
        int b = Math.min(255, (int) (baseBg.getBlue() * factor));
        return new Color(r, g, b);
    }

    public static Color getContrastingTextColor(Color bg) {
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return (brightness < 0.6) ? Color.WHITE : Color.BLACK;
    }
    
    // Default Static Fallback
    public static String getUnicodeFontName() {
        String[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        Set<String> fontSet = new HashSet<>(Arrays.asList(availableFonts));

        if (fontSet.contains("Nirmala UI")) return "Nirmala UI";
        if (fontSet.contains("Arial Unicode MS")) return "Arial Unicode MS";
        if (fontSet.contains("Noto Sans")) return "Noto Sans";
        return Font.DIALOG;
    }
    
    public static boolean isDarkTheme(Color bg) {
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return brightness < 0.5;
    }
    
    public static Color getCodeBackgroundColor(Color baseBg) {
        return getBubbleBackgroundColor(baseBg);
    }
    
    public static Color getTableBorderColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        float factor = isDark ? 1.40f : 0.85f; 
        return shiftColor(baseBg, factor);
    }

    public static Color getTableHeaderColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        float factor = isDark ? 1.20f : 0.95f;
        return shiftColor(baseBg, factor);
    }

    public static Color getTableCellColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        if (isDark) {
            return shiftColor(baseBg, 1.05f); 
        } else {
            return Color.WHITE;
        }
    }

    private static Color shiftColor(Color c, float factor) {
        int r = Math.min(255, Math.max(0, (int) (c.getRed() * factor)));
        int g = Math.min(255, Math.max(0, (int) (c.getGreen() * factor)));
        int b = Math.min(255, Math.max(0, (int) (c.getBlue() * factor)));
        return new Color(r, g, b);
    }
    
    public static void openBrowser(String url) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
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