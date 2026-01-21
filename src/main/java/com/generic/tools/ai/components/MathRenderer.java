package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.scilab.forge.jlatexmath.ParseException;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

public class MathRenderer {

    // Simple Cache: Latex String -> Icon
    private static final Map<String, Icon> ICON_CACHE = new HashMap<>();
    
    // UNIVERSAL COLOR: Medium Slate Blue (Visible on White and Dark Gray)
    private static final Color FIXED_MATH_COLOR = new Color(123, 104, 238);

    private static final Pattern MATH_BLOCK_PATTERN = Pattern.compile(
            "(\\$\\$(.*?)\\$\\$)|(\\\\\\[(.*?)\\\\\\])|(\\\\\\((.*?)\\\\\\))|(\\$([^$]+)\\$)",
            Pattern.DOTALL
    );

    public static String fixMathForTable(String text) {
        if (text == null) return "";
        Matcher m = MATH_BLOCK_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String match = m.group();
            String fixed = match.replace("|", "\\vert ");
            m.appendReplacement(sb, Matcher.quoteReplacement(fixed));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Icon latexToIcon(String rawWrapper, String latex) {
        // Ignore any color arguments, use the fixed one
        return latexToIcon(rawWrapper, latex, FIXED_MATH_COLOR); 
    }

    public static Icon latexToIcon(String rawWrapper, String latex, Color ignoredColor) {
        if (latex == null || latex.trim().isEmpty()) return null;

        // 1. Check Cache
        String key = rawWrapper + "::" + latex;
        if (ICON_CACHE.containsKey(key)) {
            return ICON_CACHE.get(key);
        }

        try {
            String cleanLatex = latex.trim();
            while (cleanLatex.endsWith("\\")) {
                cleanLatex = cleanLatex.substring(0, cleanLatex.length() - 1).trim();
            }
            cleanLatex = cleanLatex.replaceAll("\\\\text\\s*\\{", "\\\\mathrm{")
                                   .replace("\\lvert", "\\vert ")
                                   .replace("\\rvert", "\\vert ")
                                   .replace("|", "\\vert ");
                                   
            TeXFormula formula = new TeXFormula(cleanLatex);
            int style = (rawWrapper.startsWith("$$") || rawWrapper.startsWith("\\["))
                    ? TeXConstants.STYLE_DISPLAY
                    : TeXConstants.STYLE_TEXT;
            
            TeXIcon icon = formula.createTeXIcon(style, 18);
            
            // USE FIXED COLOR
            icon.setForeground(FIXED_MATH_COLOR);
            
            BufferedImage img = new BufferedImage(
                    icon.getIconWidth(),
                    icon.getIconHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            icon.paintIcon(null, g2, 0, 0);
            g2.dispose();
            
            ImageIcon finalIcon = new ImageIcon(img);
            
            // 2. Store in Cache
            ICON_CACHE.put(key, finalIcon);
            
            return finalIcon;

        } catch (ParseException e) {
            return null;
        }
    }

    public static String extractMathContent(String raw) {
        if (raw == null) return "";
        if (raw.startsWith("$$") && raw.length() >= 4) return raw.substring(2, raw.length() - 2);
        if (raw.startsWith("\\[") && raw.length() >= 4) return raw.substring(2, raw.length() - 2);
        if (raw.startsWith("\\(") && raw.length() >= 4) return raw.substring(2, raw.length() - 2);
        if (raw.startsWith("$") && raw.length() >= 2) return raw.substring(1, raw.length() - 1);
        return raw;
    }
}