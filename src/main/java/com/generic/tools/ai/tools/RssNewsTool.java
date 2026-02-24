package com.generic.tools.ai.tools;

import java.io.InputStream;
import java.net.URL;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import dev.langchain4j.agent.tool.Tool;

public class RssNewsTool {

	private static final Logger LOG = Logger.getLogger(RssNewsTool.class.getName());
	
    // Public RSS feed (no API key required)
    private static final String RSS_URL = "https://news.abplive.com/news/india/feed";

    @Tool("""
            Fetches the latest news. For each item, you MUST include:
            1. The Headline (Heading).
            2. A 2-4 line summary of the content.
            3. The clickable URL/Link labeled as 'Source'.
            """)
    public String fetchLatestHeadlines() {
        LOG.info("   --> 📰 RSS News Tool: Fetching headlines, summaries, and links");
        StringBuilder result = new StringBuilder();

        try {
            // 1. Read the feed and fix the '&' error
            String rawXml;
            try (InputStream is = new URL(RSS_URL).openStream()) {
                rawXml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            
            // Fix broken ampersands ONLY (prevents breaking links)
            String fixedXml = rawXml.replaceAll("&(?!(amp|lt|gt|quot|apos|#\\d+);)", "&amp;");

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(fixedXml.getBytes()));

            NodeList items = doc.getElementsByTagName("item");

            // Limit to top 5 to ensure the AI doesn't get overwhelmed
            for (int i = 0; i < Math.min(items.getLength(), 5); i++) {
                Element item = (Element) items.item(i);
                
                String title = getTextContent(item, "title");
                String link  = getTextContent(item, "link").trim();
                String rawDesc = getTextContent(item, "description");

                // 2. Clean the summary (2-4 lines)
                String unescaped = rawDesc.replace("&lt;", "<").replace("&gt;", ">")
                                          .replace("&quot;", "\"").replace("&amp;", "&")
                                          .replace("&rsquo;", "'").replace("&nbsp;", " ");
                
                String cleanDescription = unescaped.replaceAll("<[^>]*>", " ").trim();
                
                // Truncate to ~250 characters (about 3 lines)
                if (cleanDescription.length() > 250) {
                    cleanDescription = cleanDescription.substring(0, 247) + "...";
                }

                // 3. Construct result with Markdown (AI loves this format)
                result.append("### ").append(title).append("\n")
                      .append(cleanDescription).append("\n")
                      .append("Source: ").append(link).append("\n\n");
            }
            
            String finalOutput = result.toString().trim();
            // Log this to your console to verify the links are actually there!
            System.out.println("DEBUG TOOL OUTPUT: " + finalOutput);
            return finalOutput;

        } catch (Exception e) {
            LOG.severe("RSS Tool error: " + e.getMessage());
            return "ERROR: Unable to process news feed.";
        }
    }
    

    private String getTextContent(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? list.item(0).getTextContent() : "";
    }
}