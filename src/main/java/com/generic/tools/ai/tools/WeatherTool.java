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
        if (city == null || city.isBlank()) {
            return "❌ No city provided.";
        }
                
        LOG.info("--> 🌤️ Weather Tool: " + city);

        String cleanCity = city.replaceAll("[\\[\\]\"']", "").trim();

        // Guard: one city only
        if (cleanCity.contains(",") || cleanCity.toLowerCase().contains(" and ")) {
            return "❌ Multiple cities detected. Please call this tool with a single city at a time.";
        }

        try {
            String encodedCity = URLEncoder.encode(cleanCity, StandardCharsets.UTF_8);
            String url = "https://wttr.in/" + encodedCity + "?format=3";

            String response = httpClient.get(url);

            // wttr.in returns error messages starting with "Unknown location"
            if (response.toLowerCase().contains("unknown location")) {
                return cleanCity + ": ❌ Location not found.";
            }

            return response.trim();

        } catch (Exception e) {
            return cleanCity + ": ❌ Weather data temporarily unavailable.";
        }
    }
}
