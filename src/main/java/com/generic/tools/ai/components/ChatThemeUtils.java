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
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

public class ChatThemeUtils {

    // --- CORE FIX: Robust Smart Insert ---
    public static void smartInsert(StyledDocument doc, String text, AttributeSet baseAttr) throws BadLocationException {
        if (text == null || text.isEmpty()) return;

        // 1. CHECK FOR COMPLEX CHARACTERS
        // If the text contains ANY non-ASCII character (like Emojis, Math symbols, or accented chars),
        // we SKIP the "Fast Path". "Dialog" font often lies about supporting these in Tables.
        boolean hasComplexChars = false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                hasComplexChars = true;
                break;
            }
        }

        // 2. FAST PATH (Only for pure English/ASCII)
        // This keeps the chat fast for normal text.
        if (!hasComplexChars) {
            doc.insertString(doc.getLength(), text, baseAttr);
            return;
        }

        // 3. SLOW PATH (Character-by-Character Font Check)
        // We forcibly find the best font for every single special character.
        int index = 0;
        Font defaultFont = getFontFromAttr(baseAttr);
        
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            String singleChar = new String(Character.toChars(codePoint));

            // Check if standard font handles it VISUALLY (not just logically)
            // We use a stricter check here: if it's complex, we prefer the Smart Font lookup
            // unless the default font is ALREADY a specific Unicode font.
            boolean useDefault = (codePoint <= 127) || (defaultFont.canDisplayUpTo(singleChar) == -1);

            // Double-check: If it's an Emoji/Symbol, forcing lookup is safer than trusting 'useDefault'
            if (isEmojiOrSymbol(codePoint)) {
                useDefault = false; 
            }

            if (useDefault) {
                doc.insertString(doc.getLength(), singleChar, baseAttr);
            } else {
                // Find the specific font that supports this character (e.g., Segoe UI Emoji)
                String fallbackFontName = getSmartFontName(singleChar);
                
                SimpleAttributeSet fallbackAttrs = new SimpleAttributeSet(baseAttr);
                StyleConstants.setFontFamily(fallbackAttrs, fallbackFontName);
                
                doc.insertString(doc.getLength(), singleChar, fallbackAttrs);
            }
            
            index += charCount;
        }
    }
    
    // Helper to identify characters that often break "Dialog" font
    private static boolean isEmojiOrSymbol(int codePoint) {
        return (codePoint >= 0x2000 && codePoint <= 0x3300) || // General Symbols / Dingbats
               (codePoint >= 0x1F000); // Emojis / Supplemental
    }

    private static Font getFontFromAttr(AttributeSet attr) {
        String family = StyleConstants.getFontFamily(attr);
        int style = Font.PLAIN;
        if (StyleConstants.isBold(attr)) style |= Font.BOLD;
        if (StyleConstants.isItalic(attr)) style |= Font.ITALIC;
        int size = StyleConstants.getFontSize(attr);
        
        if (family == null) family = Font.DIALOG;
        return StyleContext.getDefaultStyleContext().getFont(family, style, size);
    }

    public static String getSmartFontName(String text) {
        if (text == null || text.trim().isEmpty()) return getUnicodeFontName();

        // Priority List
        String[] priorities = {
            "Segoe UI Emoji",   // Windows: Best for Emojis
            "Apple Color Emoji",// Mac: Best for Emojis
            "Nirmala UI",       // Windows: Good for Indic/Symbols
            "Segoe UI Symbol",  // Windows: Math/Symbols
            "Cambria Math",     
            "Arial Unicode MS", 
            "Noto Color Emoji", // Linux
            "Noto Sans",
            Font.SANS_SERIF, 
            Font.DIALOG
        };
        
        for (String fontName : priorities) {
            if (!isFontAvailable(fontName)) continue;

            Font f = new Font(fontName, Font.PLAIN, 14);
            if (f.canDisplayUpTo(text) == -1) {
                return fontName;
            }
        }
        
        return getUnicodeFontName();
    }
    
    private static Set<String> availableFontsCache;
    private static boolean isFontAvailable(String fontName) {
        if (availableFontsCache == null) {
            availableFontsCache = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
            ));
        }
        return availableFontsCache.contains(fontName) || 
               fontName.equals(Font.SANS_SERIF) || 
               fontName.equals(Font.DIALOG);
    }

    // --- Standard Theme Helpers ---
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
    
    public static String getUnicodeFontName() {
        if (availableFontsCache == null) isFontAvailable("Dialog"); 
        if (availableFontsCache.contains("Nirmala UI")) return "Nirmala UI";
        if (availableFontsCache.contains("Arial Unicode MS")) return "Arial Unicode MS";
        if (availableFontsCache.contains("Noto Sans")) return "Noto Sans";
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