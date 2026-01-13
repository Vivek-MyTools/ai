package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.GraphicsEnvironment;

public class ChatThemeUtils {

    public static String getUnicodeFontName() {
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String fontName : fontNames) {
            if (fontName.equalsIgnoreCase("Nirmala UI")) return "Nirmala UI";
        }
        for (String fontName : fontNames) {
            if (fontName.equalsIgnoreCase("Arial Unicode MS")) return "Arial Unicode MS";
        }
        return "Dialog";
    }

    public static boolean isDarkTheme(Color bg) {
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return brightness < 0.5;
    }

    public static Color getCodeBackgroundColor(Color baseBg) {
        boolean isDark = isDarkTheme(baseBg);
        float factor = isDark ? 1.10f : 0.95f;
        int r = Math.min(255, (int) (baseBg.getRed() * factor));
        int g = Math.min(255, (int) (baseBg.getGreen() * factor));
        int b = Math.min(255, (int) (baseBg.getBlue() * factor));
        return new Color(r, g, b);
    }
}