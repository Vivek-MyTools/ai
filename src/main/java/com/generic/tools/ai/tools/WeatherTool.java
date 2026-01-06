package com.generic.tools.ai.tools;

import dev.langchain4j.agent.tool.P; // 👈 1. IMPORT THIS
import dev.langchain4j.agent.tool.Tool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WeatherTool {

    private final HttpClientWrapper httpClient;

    // 1️⃣ Default constructor
    public WeatherTool() {
        this(new JavaHttpClientWrapper());
    }

    // 2️⃣ Test constructor
    public WeatherTool(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Tool("""
            Returns current weather conditions (temperature, rain, etc.) for a city.
            Returns a simple text summary.
            """)
    public String getWeather(
            // 👈 2. VITAL FOR GEMINI: Explicitly describe the parameter
            @P("The name of the city, e.g. 'Bangalore' or 'New York'") String city
    ) {
        System.out.println("Using Tool: Weather lookup for " + city);

        if (city == null || city.isBlank()) {
            return "Error: No city name provided.";
        }

        // 👈 3. IMPROVED SANITIZATION:
        // Gemini sometimes passes args like "['Bangalore']" or "\"Bangalore\""
        String cleanCity = city.replaceAll("[\\[\\]\"']", "").trim();

        // Handle comma-separated cities (Recursion)
        if (cleanCity.contains(",")) {
            StringBuilder result = new StringBuilder();
            for (String c : cleanCity.split(",")) {
                result.append(getWeather(c.trim())).append("\n");
            }
            return result.toString().trim();
        }

        try {
            // wttr.in is great, but ensure your HttpClient handles redirects/User-Agent if needed
            String encodedCity = URLEncoder.encode(cleanCity, StandardCharsets.UTF_8);
            String url = "https://wttr.in/" + encodedCity + "?format=3";

            return httpClient.get(url);

        } catch (Exception e) {
            // Log the actual error to your console so you can see if it's a 403/404/Timeout
            System.err.println("Weather Tool Error: " + e.getMessage());
            return "Error fetching weather for " + cleanCity + ": " + e.getMessage();
        }
    }
}