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
        try {
            if (latex == null || latex.trim().isEmpty()) return null;

            TeXFormula formula = new TeXFormula(latex);
            
            // Determine style based on wrapper ($$ vs $)
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
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            icon.paintIcon(null, g2, 0, 0);
            g2.dispose();

            return new ImageIcon(img);
        } catch (ParseException e) {
            return null;
        }
    }

    public static String extractMathContent(String raw) {
        if (raw.startsWith("$$")) return raw.substring(2, raw.length() - 2);
        if (raw.startsWith("\\[")) return raw.substring(2, raw.length() - 2);
        if (raw.startsWith("\\(")) return raw.substring(2, raw.length() - 2);
        if (raw.startsWith("$")) return raw.substring(1, raw.length() - 1);
        return raw;
    }
}