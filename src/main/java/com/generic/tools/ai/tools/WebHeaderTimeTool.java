package com.generic.tools.ai.tools;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.logging.Logger;

import dev.langchain4j.agent.tool.Tool;

public class WebHeaderTimeTool {
	
	private static final Logger LOG = Logger.getLogger(WebHeaderTimeTool.class.getName());

    private static final String TARGET_URL = "https://www.google.com";

    @Tool("""
        Returns the current date and time for a specific city or time zone.
        Usage:
        - If the user asks 'What time is it in Moscow?', pass 'Moscow'.
        - If the user asks 'Current time', pass 'default'.
        """)
    public String getWebTime(String query) {
        try {
        	LOG.info("  --> ⏰ WebHeader-Time Tool: " + query);
            // 1. Fetch the absolute UTC time from Google (Bypassing local clock)
            URL url = new URL(TARGET_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("HEAD");
            con.setConnectTimeout(2000);
            con.connect();

            long dateHeader = con.getDate();
            if (dateHeader == 0) return "Error: Server returned no date.";

            // 2. Resolve the requested Time Zone (e.g., "Moscow" -> "Europe/Moscow")
            ZoneId targetZone = resolveTimeZone(query);

            // 3. Convert the UTC timestamp to the Target Zone
            ZonedDateTime targetTime = ZonedDateTime.parse(
                new Date(dateHeader).toInstant().toString(),
                DateTimeFormatter.ISO_INSTANT.withZone(targetZone)
            );

            // 4. Return the formatted string
            return "Location: " + targetZone + "\n" +
                   "Time: " + targetTime.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy, HH:mm:ss z"));

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Helper: Converts "Moscow" -> "Europe/Moscow"
    private ZoneId resolveTimeZone(String query) {
        if (query == null || query.isBlank() || query.equalsIgnoreCase("default")) {
            return ZoneId.systemDefault(); // Fallback to local if no city asked
        }

        String q = query.toLowerCase().replace(" ", "_");

        // 1. Common Shortcuts (Optional, but helps accuracy)
        if (q.contains("moscow")) return ZoneId.of("Europe/Moscow");
        if (q.contains("new_york")) return ZoneId.of("America/New_York");
        if (q.contains("london")) return ZoneId.of("Europe/London");
        if (q.contains("tokyo")) return ZoneId.of("Asia/Tokyo");
        if (q.contains("utc") || q.contains("gmt")) return ZoneId.of("UTC");

        // 2. Scan Java's available zones for a match
        for (String zone : ZoneId.getAvailableZoneIds()) {
            if (zone.toLowerCase().contains(q)) {
                return ZoneId.of(zone);
            }
        }

        // 3. Fallback
        return ZoneId.systemDefault();
    }
}