package com.generic.tools.ai.tools;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import dev.langchain4j.agent.tool.Tool;

public class TimeTool {

    private static final Logger LOG = Logger.getLogger(TimeTool.class.getName());

    private static final DateTimeFormatter OUTPUT_FMT =
        DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy, hh:mm a z");

    private final HttpClientWrapper httpClient;

    public TimeTool() {
        this(new JavaHttpClientWrapper());
    }

    public TimeTool(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Tool("""
        Returns the current real-world date and time.
        Usage:
        - Use whenever the user asks for the time, date, or day.
        - If a city is specified (e.g., 'New York'), pass the city name.
        - If no city is specified, pass 'default'.
        """)
    public String getCurrentTime(String query) {
        String zoneId = resolveTimeZone(query);
        ZoneId zone = ZoneId.of(zoneId);

        LOG.info("⏰ TimeTool invoked for zone: " + zoneId);

        // -------- Primary: timeapi.io --------
        try {
            String url =
                "https://timeapi.io/api/Time/current/zone?timeZone="
                + URLEncoder.encode(zoneId, StandardCharsets.UTF_8);

            String json = httpClient.get(url);

            if (json != null && json.contains("\"dateTime\"")) {
                String dateTime = extract(json, "dateTime");

                ZonedDateTime zdt = ZonedDateTime.parse(dateTime)
                        .withZoneSameInstant(zone);

                return format(zoneId, zdt);
            }
        } catch (Exception e) {
            LOG.warning("Primary time server failed: " + e.getMessage());
        }

        // -------- Backup: worldtimeapi.org --------
        try {
            String url = "http://worldtimeapi.org/api/timezone/" + zoneId;
            String json = httpClient.get(url);

            if (json != null && json.contains("\"datetime\"")) {
                String datetime = extract(json, "datetime");

                OffsetDateTime odt = OffsetDateTime.parse(datetime);
                ZonedDateTime zdt = odt.atZoneSameInstant(zone);

                return format(zoneId, zdt);
            }
        } catch (Exception e) {
            LOG.warning("Backup time server failed: " + e.getMessage());
        }

        return "Error: Unable to retrieve current time from external servers.";
    }

    // ------------------------------------------------------

    private String format(String zoneId, ZonedDateTime zdt) {
        String location =
            zoneId.startsWith("Asia/Kolkata") ? "India" : zoneId.replace('_', ' ');

        return "The current time in " + location + " is:\n\n"
                + zdt.format(OUTPUT_FMT);
    }

    private String extract(String json, String field) {
        int start = json.indexOf("\"" + field + "\":");
        if (start < 0) return null;

        start = json.indexOf('"', start + field.length() + 2) + 1;
        int end = json.indexOf('"', start);

        return json.substring(start, end);
    }

    private String resolveTimeZone(String query) {
        if (query == null || query.isBlank() || query.equalsIgnoreCase("default")) {
            return "Asia/Kolkata";
        }

        String q = query.toLowerCase().replace(" ", "_");

        if (q.contains("new_york")) return "America/New_York";
        if (q.contains("london")) return "Europe/London";
        if (q.contains("tokyo")) return "Asia/Tokyo";
        if (q.contains("sydney")) return "Australia/Sydney";
        if (q.contains("utc") || q.contains("gmt")) return "Etc/UTC";

        for (String zone : ZoneId.getAvailableZoneIds()) {
            if (zone.toLowerCase().contains(q)) {
                return zone;
            }
        }

        return "Asia/Kolkata";
    }
}
