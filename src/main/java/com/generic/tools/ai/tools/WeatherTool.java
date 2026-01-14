package com.generic.tools.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class WeatherTool {

    private static final Logger LOG = Logger.getLogger(WeatherTool.class.getName());
    private final HttpClientWrapper httpClient = new JavaHttpClientWrapper();

    @Tool("""
        Returns current weather conditions for cities using wttr.in.
        
        Guidelines:
        - You can accept multiple city names separated by commas or 'and'.
        - The agent should split cities and call this tool separately for each city.
        - Returns a simple summary with emoji, temperature (°C), and basic condition.
        
        Example input: "Kochi"
        Example output: "Kochi: 🌤 +28°C"
        """)
    public String getWeather(
            @P("Name of a single city, e.g., 'Delhi', 'Kochi', or 'New York'. For multiple cities, the agent can split input and call separately.")
            String city
    ) {
        // 1. INPUT GUARD: Null or Empty Input
        if (city == null || city.isBlank()) {
            return "❌ Error: City name was missing or empty.";
        }
                
        LOG.info("--> 🌤️ Weather Tool: " + city);

        String cleanCity = city.replaceAll("[\\[\\]\"']", "").trim();

        // 2. INPUT GUARD: Multi-city detection
        if (cleanCity.contains(",") || cleanCity.toLowerCase().contains(" and ")) {
            return "❌ Multiple cities detected. Please call this tool with a single city at a time.";
        }

        try {
            String encodedCity = URLEncoder.encode(cleanCity, StandardCharsets.UTF_8);
            // format=3 gives simple "City: Temp" output
            String url = "https://wttr.in/" + encodedCity + "?format=3";

            // 3. API CALL
            String response = httpClient.get(url);

            // 4. RESPONSE GUARD: Check for Null/Empty BEFORE using it
            if (response == null || response.isBlank()) {
                LOG.warning("Weather API returned null/empty for: " + cleanCity);
                return cleanCity + ": ❌ No data received from weather service.";
            }

            // 5. ERROR CONTENT GUARD: Check for known API errors
            String lowerResp = response.toLowerCase();
            if (lowerResp.contains("unknown location") || lowerResp.contains("404 not found")) {
                return cleanCity + ": ❌ Location not found.";
            }

            return response.trim();

        } catch (Exception e) {
            // 6. EXCEPTION GUARD: Catch crashes and return string
            LOG.severe("Weather Tool Error: " + e.getMessage());
            return cleanCity + ": ❌ Weather data unavailable (Error: " + e.getMessage() + ")";
        }
    }
}