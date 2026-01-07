package com.generic.tools.ai.tools;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.logging.Logger;

import dev.langchain4j.agent.tool.Tool;

public class TimeTool {

	private static final Logger LOG = Logger.getLogger(TimeTool.class.getName());
	
    private final HttpClientWrapper httpClient;

    public TimeTool() {
        this(new JavaHttpClientWrapper());
    }

    public TimeTool(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Tool("""
        Returns the current real-world date and time from external servers.
        Usage:
        - Use this whenever the user asks for the time, date, or day of the week.
        - If the user asks for a specific city (e.g., 'New York'), pass that city name.
        - If no city is specified, pass 'default'.
        """)
    public String getCurrentTime(String query) {
        String timeZoneId = resolveTimeZone(query);
        String result;
        
        LOG.info("  --> ⏰ Time Tool: " + query);
          
        
        // --- ATTEMPT 1: timeapi.io ---
        try {
            LOG.info("DEBUG: Trying Primary Time Server (timeapi.io)...");
            String url = "https://timeapi.io/api/Time/current/zone?timeZone=" 
                         + URLEncoder.encode(timeZoneId, StandardCharsets.UTF_8);
            
            result = httpClient.get(url);
            
            // Basic validation
            if (result != null && result.contains("dateTime")) {
                return "Source: timeapi.io\nData: " + result;
            }
        } catch (Exception e) {
            LOG.severe("Primary server failed: " + e.getMessage());
        }

        // --- ATTEMPT 2: worldtimeapi.org (Backup) ---
        try {
            LOG.info("DEBUG: Trying Backup Time Server (worldtimeapi.org)...");
            String url = "http://worldtimeapi.org/api/timezone/" + timeZoneId;
            
            result = httpClient.get(url);
            
            if (result != null && result.contains("datetime")) {
                return "Source: worldtimeapi.org\nData: " + result;
            }
        } catch (Exception e) {
            LOG.severe("Backup server failed: " + e.getMessage());
        }

        // --- FAILURE: No System Clock Fallback ---
        return "Error: Unable to retrieve time. All external time servers are currently unreachable.";
    }

    private String resolveTimeZone(String query) {
        if (query == null || query.isBlank() || query.equalsIgnoreCase("default")) {
            return "Asia/Kolkata"; 
        }

        String q = query.toLowerCase().replace(" ", "_");
        
        // Map common inputs to IANA TimeZone IDs required by these APIs
        if (q.contains("new_york")) return "America/New_York";
        if (q.contains("london")) return "Europe/London";
        if (q.contains("tokyo")) return "Asia/Tokyo";
        if (q.contains("sydney")) return "Australia/Sydney";
        if (q.contains("utc") || q.contains("gmt")) return "Etc/UTC";

        // Try exact match against Java's known zones
        for (String zone : ZoneId.getAvailableZoneIds()) {
            if (zone.toLowerCase().contains(q)) {
                return zone;
            }
        }
        return "Asia/Kolkata"; // Default if unknown
    }
}