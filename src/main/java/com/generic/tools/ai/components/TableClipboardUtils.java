package com.generic.tools.ai.components;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * Handles all Clipboard operations and Context Menu logic for Tables.
 */
public class TableClipboardUtils {

    /**
     * Attaches the "Copy" context menu to a specific component.
     */
    public static void attachCopyMenu(JComponent component, String rawMarkdown) {
        JPopupMenu popup = new JPopupMenu();
        
        JMenuItem copyMd = new JMenuItem("Copy Table (Markdown)");
        copyMd.addActionListener(e -> copyToClipboard(rawMarkdown));
        
        JMenuItem copyHtml = new JMenuItem("Copy for Word/Excel");
        copyHtml.addActionListener(e -> {
            String flattened = flattenLatex(rawMarkdown.trim());
            String html = convertToHtml(flattened);
            String text = convertToTsv(flattened);
            copyHtmlToClipboard(html, text);
        });
        
        popup.add(copyMd);
        popup.add(copyHtml);

        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { handle(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handle(e); }
            
            private void handle(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Flattens multi-line LaTeX blocks to ensure table rows don't break.
     */
    public static String flattenLatex(String text) {
        Pattern p = Pattern.compile("(?s)(\\$\\$.*?\\$\\$|\\\\\\\\\\[.*?\\\\\\\\])");
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
             String block = m.group();
             block = block.replace("\n", " ").replace("\r", "");
             m.appendReplacement(sb, Matcher.quoteReplacement(block));
        }
        m.appendTail(sb);
        return sb.toString();
    }
    
    // --- Internal Helpers ---

    private static void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }
    
    private static void copyHtmlToClipboard(String html, String plainText) {
        HtmlTransferable transferable = new HtmlTransferable(html, plainText);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
    }
    
    private static String convertToTsv(String markdown) {
        StringBuilder sb = new StringBuilder();
        String[] lines = markdown.split("\\n");
        for (String line : lines) {
            if (line.contains("---")) continue; 
            String temp = cleanCell(line);
            String[] parts = temp.split("\\|");
            for (int i = 0; i < parts.length; i++) {
                sb.append(parts[i].trim());
                if (i < parts.length - 1) sb.append("\t");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String convertToHtml(String markdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><table border='1' style='border-collapse: collapse; font-family: sans-serif;'>");
        
        String[] lines = markdown.split("\\n");
        int rowCount = 0;
        
        for (String line : lines) {
            if (line.contains("---")) continue; 
            
            String temp = cleanCell(line);
            String[] cells = temp.split("\\|");
            sb.append("<tr>");
            
            for (String cell : cells) {
                String content = cell.trim();
                content = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                content = content.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
                content = content.replaceAll("\\*(.*?)\\*", "<i>$1</i>");
                
                String style = "padding: 5px;";
                if (rowCount == 0) {
                    style += " background-color: #f2f2f2; font-weight: bold;";
                    sb.append("<th style='").append(style).append("'>").append(content).append("</th>");
                } else {
                    sb.append("<td style='").append(style).append("'>").append(content).append("</td>");
                }
            }
            sb.append("</tr>");
            rowCount++;
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }
    
    private static String cleanCell(String line) {
        String temp = line.trim();
        if (temp.startsWith("|")) temp = temp.substring(1);
        if (temp.endsWith("|")) temp = temp.substring(0, temp.length() - 1);
        return temp;
    }

    private static class HtmlTransferable implements Transferable {
        private final String html;
        private final String plainText;

        public HtmlTransferable(String html, String plainText) {
            this.html = html;
            this.plainText = plainText;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            try {
                return new DataFlavor[]{
                    new DataFlavor("text/html;class=java.lang.String"),
                    DataFlavor.stringFlavor
                };
            } catch (ClassNotFoundException e) {
                return new DataFlavor[0];
            }
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            for (DataFlavor f : getTransferDataFlavors()) if (f.equals(flavor)) return true;
            return false;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (DataFlavor.stringFlavor.equals(flavor)) return plainText;
            else if (flavor.isMimeTypeEqual("text/html")) return html;
            throw new UnsupportedFlavorException(flavor);
        }
    }
}