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
            Fetches the latest world news headlines from a public RSS feed.

            STRICT USAGE RULES:
            1. Use this tool ONLY when the user explicitly asks for latest news or headlines.
            2. DO NOT use this tool for summarization, opinions, or historical news analysis.
            3. DO NOT fabricate or guess headlines.
            4. Output MUST be raw headline titles with their corresponding URLs.
            5. If the user asks for explanation or summary, call this tool FIRST, then summarize.
            """)
    public String fetchLatestHeadlines() {
        LOG.info("   --> 📰 RSS News Tool: Fetching latest headlines");

        StringBuilder result = new StringBuilder();
        try (InputStream stream = new URL(RSS_URL).openStream()) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(stream);

            NodeList items = doc.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);

                String title = getTextContent(item, "title");
                String link  = getTextContent(item, "link");

                result.append(i + 1)
                      .append(". ")
                      .append(title)
                      .append("\n   ")
                      .append(link)
                      .append("\n\n");
            }

            return result.toString().trim();

        } catch (Exception e) {
            System.err.println("RSS Tool error: " + e.getMessage());
            return "ERROR: Unable to fetch latest news";
        }
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? list.item(0).getTextContent() : "";
    }
}
