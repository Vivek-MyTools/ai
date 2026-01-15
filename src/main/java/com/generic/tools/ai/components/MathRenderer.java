package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.scilab.forge.jlatexmath.ParseException;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

public class MathRenderer {

    public static Icon latexToIcon(String rawWrapper, String latex) {
        return latexToIcon(rawWrapper, latex, new Color(123, 104, 238)); 
    }

    public static Icon latexToIcon(String rawWrapper, String latex, Color color) {
        try {
            if (latex == null || latex.trim().isEmpty()) return null;

            String cleanLatex = latex.trim();
            
            // Clean up LaTeX
            while (cleanLatex.endsWith("\\")) {
                cleanLatex = cleanLatex.substring(0, cleanLatex.length() - 1).trim();
            }
            cleanLatex = cleanLatex.replaceAll("\\\\text\\s*\\{", "\\\\mathrm{")
                                   .replace("\\lvert", "\\vert")
                                   .replace("\\rvert", "\\vert");

            TeXFormula formula = new TeXFormula(cleanLatex);
            int style = (rawWrapper.startsWith("$$") || rawWrapper.startsWith("\\["))
                    ? TeXConstants.STYLE_DISPLAY
                    : TeXConstants.STYLE_TEXT;

            TeXIcon icon = formula.createTeXIcon(style, 18);
            icon.setForeground(new Color(123, 104, 238));

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

            return new ImageIcon(img);
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