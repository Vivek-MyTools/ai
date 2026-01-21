package com.generic.tools.ai.components;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.scilab.forge.jlatexmath.ParseException;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

public class MathRenderer {

    // Regex to find math blocks: $$...$$, \[...\], \(...\), or $...$
    // We strictly look for blocks to avoid breaking actual markdown table pipes
    private static final Pattern MATH_BLOCK_PATTERN = Pattern.compile(
            "(\\$\\$(.*?)\\$\\$)|(\\\\\\[(.*?)\\\\\\])|(\\\\\\((.*?)\\\\\\))|(\\$([^$]+)\\$)",
            Pattern.DOTALL
    );

    /**
     * Call this method on your raw Markdown string BEFORE passing it to your Table/Markdown parser.
     * It replaces '|' with '\vert' inside math blocks so tables don't split the equation.
     */
    public static String fixMathForTable(String text) {
        if (text == null) return "";
        
        Matcher m = MATH_BLOCK_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (m.find()) {
            String match = m.group();
            // Replace raw pipes | with \vert inside the math block
            String fixed = match.replace("|", "\\vert");
            m.appendReplacement(sb, Matcher.quoteReplacement(fixed));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Icon latexToIcon(String rawWrapper, String latex) {
        // Default color matches your previous code
        return latexToIcon(rawWrapper, latex, new Color(123, 104, 238)); 
    }

    public static Icon latexToIcon(String rawWrapper, String latex, Color color) {
        try {
            if (latex == null || latex.trim().isEmpty()) return null;

            String cleanLatex = latex.trim();
            
            // Clean up LaTeX trailing slashes
            while (cleanLatex.endsWith("\\")) {
                cleanLatex = cleanLatex.substring(0, cleanLatex.length() - 1).trim();
            }
            
            // formatting replacements
            cleanLatex = cleanLatex.replaceAll("\\\\text\\s*\\{", "\\\\mathrm{")
                                   .replace("\\lvert", "\\vert")
                                   .replace("\\rvert", "\\vert")
                                   // Safety: ensure pipes are treated as \vert here too
                                   .replace("|", "\\vert");

            TeXFormula formula = new TeXFormula(cleanLatex);
            
            // Determine style
            int style = (rawWrapper.startsWith("$$") || rawWrapper.startsWith("\\["))
                    ? TeXConstants.STYLE_DISPLAY
                    : TeXConstants.STYLE_TEXT;

            TeXIcon icon = formula.createTeXIcon(style, 18);
            icon.setForeground(color != null ? color : new Color(123, 104, 238));

            BufferedImage img = new BufferedImage(
                    icon.getIconWidth(),
                    icon.getIconHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g2 = img.createGraphics();
            
            // High quality rendering hints
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            
            icon.paintIcon(null, g2, 0, 0);
            g2.dispose();

            return new ImageIcon(img);
        } catch (ParseException e) {
            // Log error if needed, or return null/error icon
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