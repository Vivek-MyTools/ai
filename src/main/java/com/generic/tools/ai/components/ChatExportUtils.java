package com.generic.tools.ai.components;

import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.Graphics2D;
import java.io.File;
import java.io.FileOutputStream;

public class ChatExportUtils {

    public static void exportToPdf(JComponent component, File file) {
        // 1. Setup PDF Document (A4 Size)
        Document document = new Document(com.lowagie.text.PageSize.A4);
        
        try {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            // 2. Get the content size
            double compWidth = component.getWidth();
            double compHeight = component.getHeight();
            
            // 3. Calculate Scaling to fit A4 width
            // A4 is approx 595 x 842 points. We leave some margin.
            double pdfPageWidth = document.getPageSize().getWidth();
            double pdfPageHeight = document.getPageSize().getHeight();
            
            // Scale factor to fit the component width into the PDF page width
            double scale = pdfPageWidth / compWidth;
            
            // Calculate how much height fits on one PDF page (accounting for scale)
            double scaledPageHeight = pdfPageHeight / scale;

            // 4. Loop through the component height and print pages
            double yOffset = 0;
            
            PdfContentByte contentByte = writer.getDirectContent();

            while (yOffset < compHeight) {
                // Create a new page (except for the first loop where doc is already open)
                if (yOffset > 0) {
                    document.newPage();
                }

                // Create a template (a graphics layer)
                PdfTemplate template = contentByte.createTemplate((float) compWidth, (float) scaledPageHeight);
                Graphics2D g2d = template.createGraphics((float) compWidth, (float) scaledPageHeight);

                // 5. Translate (Shift) the camera down to print the next section
                g2d.translate(0, -yOffset);
                
                // Disable double buffering to ensure high-quality vector text
                disableDoubleBuffering(component);
                
                // 6. Tell the component to print itself onto the PDF Graphics
                // printAll renders children (tables, code blocks) correctly
                component.printAll(g2d);
                
                g2d.dispose();
                enableDoubleBuffering(component);

                // 7. Add the template to the PDF page with scaling
                contentByte.addTemplate(template, (float) scale, 0, 0, (float) scale, 0, 0);

                yOffset += scaledPageHeight;
            }

            document.close();
            JOptionPane.showMessageDialog(null, "PDF Exported Successfully!\n" + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error exporting PDF: " + e.getMessage());
        }
    }

    // Helper to ensure crisp rendering
    private static void disableDoubleBuffering(Component c) {
        if (c instanceof JComponent) {
            ((JComponent) c).setDoubleBuffered(false);
        }
        if (c instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) c).getComponents()) {
                disableDoubleBuffering(child);
            }
        }
    }

    private static void enableDoubleBuffering(Component c) {
        if (c instanceof JComponent) {
            ((JComponent) c).setDoubleBuffered(true);
        }
        if (c instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) c).getComponents()) {
                enableDoubleBuffering(child);
            }
        }
    }
}