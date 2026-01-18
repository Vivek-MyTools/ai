package com.generic.tools.ai.components;

import com.generic.tools.lnf.theme.LnfTheme;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class TableClipboardUtils {

    // Singleton UI elements
    private static JPopupMenu sharedPopup;
    private static String activeMarkdownPayload; // Holds data for the currently clicked table
    
    // Keys for component tagging
    private static final String KEY_LISTENER_ATTACHED = "TableUtils_ListenerAttached";
    private static final String KEY_MARKDOWN_DATA = "TableUtils_MarkdownData";

    /**
     * Attaches the context menu. 
     * Safe to call multiple times on the same component (updates data, doesn't duplicate listeners).
     */
    public static void attachCopyMenu(JComponent component, String rawMarkdown) {
        // 1. Store the specific data into the component itself
        component.putClientProperty(KEY_MARKDOWN_DATA, rawMarkdown);

        // 2. Only add the listener if it hasn't been added yet
        if (component.getClientProperty(KEY_LISTENER_ATTACHED) == null) {
            component.addMouseListener(new MouseAdapter() {
                private void handleEvent(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showPopup(e);
                    }
                }
                public void mousePressed(MouseEvent e) { handleEvent(e); }
                public void mouseReleased(MouseEvent e) { handleEvent(e); }
            });
            component.putClientProperty(KEY_LISTENER_ATTACHED, Boolean.TRUE);
        }
    }

    private static void showPopup(MouseEvent e) {
        JComponent c = (JComponent) e.getComponent();
        Object data = c.getClientProperty(KEY_MARKDOWN_DATA);

        if (data instanceof String) {
            // Update the shared state
            activeMarkdownPayload = (String) data;
            
            // Lazy load the popup once
            if (sharedPopup == null) {
                initPopup();
            }
            sharedPopup.show(c, e.getX(), e.getY());
        }
    }

    private static void initPopup() {
        sharedPopup = new JPopupMenu();
        LnfTheme.getInstance().addComponent(sharedPopup);
        JMenuItem copyMd = new JMenuItem("Copy Table (Markdown)");
        copyMd.addActionListener(ev -> {
            if (activeMarkdownPayload != null) copyToClipboard(activeMarkdownPayload);
        });

        JMenuItem copyHtml = new JMenuItem("Copy for Word/Excel");
        copyHtml.addActionListener(ev -> {
            if (activeMarkdownPayload != null) {
                String flattened = flattenLatex(activeMarkdownPayload.trim());
                copyHtmlToClipboard(convertToHtml(flattened), convertToTsv(flattened));
            }
        });

        sharedPopup.add(copyMd);
        sharedPopup.add(copyHtml);
    }

    // --- Utility Methods (Kept Public/Private as in original) ---

    // Fixes LaTeX breaking tables (Public as requested)
    public static String flattenLatex(String text) {
        Pattern p = Pattern.compile("(?s)(\\$\\$.*?\\$\\$|\\\\\\\\\\[.*?\\\\\\\\])");
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(m.group().replace("\n", " ").replace("\r", "")));
        m.appendTail(sb);
        return sb.toString();
    }

    private static void copyToClipboard(String text) {
        StringSelection s = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(s, s);
    }

    private static void copyHtmlToClipboard(String html, String plain) {
        HtmlTransferable t = new HtmlTransferable(html, plain);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
    }

    private static String convertToTsv(String md) {
        StringBuilder sb = new StringBuilder();
        for (String line : md.split("\\n")) {
            if (line.contains("---")) continue;
            String t = line.trim().replaceAll("^\\||\\|$", "");
            String[] parts = t.split("\\|");
            for (int i=0; i<parts.length; i++) sb.append(parts[i].trim()).append(i<parts.length-1?"\t":"");
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String convertToHtml(String md) {
        StringBuilder sb = new StringBuilder("<html><body><table border='1' style='border-collapse:collapse;font-family:sans-serif;'>");
        int r=0;
        for (String line : md.split("\\n")) {
            if (line.contains("---")) continue;
            String t = line.trim().replaceAll("^\\||\\|$", "");
            sb.append("<tr>");
            for (String c : t.split("\\|")) {
                String tag = (r==0) ? "th" : "td";
                String style = "padding:5px;" + (r==0?"background-color:#f2f2f2;font-weight:bold;":"");
                sb.append("<").append(tag).append(" style='").append(style).append("'>")
                  .append(c.trim().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"))
                  .append("</").append(tag).append(">");
            }
            sb.append("</tr>"); r++;
        }
        return sb.append("</table></body></html>").toString();
    }

    private static class HtmlTransferable implements Transferable {
        private String h, p;
        public HtmlTransferable(String h, String p) { this.h=h; this.p=p; }
        public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{ new DataFlavor("text/html;class=java.lang.String", null), DataFlavor.stringFlavor }; }
        public boolean isDataFlavorSupported(DataFlavor f) { return f.equals(DataFlavor.stringFlavor) || f.isMimeTypeEqual("text/html"); }
        public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException { if(f.equals(DataFlavor.stringFlavor))return p; return h; }
    }
}