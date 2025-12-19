package com.generic.tools.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class WeatherTool {

    private final HttpClientWrapper httpClient;

    // 1️⃣ Default constructor (USED BY MAIN APP)
    public WeatherTool() {
        this(new JavaHttpClientWrapper());
    }

    // 2️⃣ Test constructor (USED BY TESTS)
    public WeatherTool(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Tool("""
            Returns current weather conditions (temperature, rain, etc.) for a city.
            Usage Rules:
            1. Use this tool ONLY if the user explicitly asks for 'weather', 'temperature', or 'forecast'.
            2. Do NOT use this tool for questions about people, authors, events, or projects.
            3. If no city is specified, ask the user for the city first.
            """)
    public String getWeather(String city) {

        System.out.println("Using Tool: Weather lookup for " + city);

        if (city == null || city.isBlank()) {
            return "Error: No city name provided.";
        }

        // Sanitize model noise
        String cleanCity = city.replace("[", "")
                               .replace("]", "")
                               .replace("\"", "");

        // Handle comma-separated cities
        if (cleanCity.contains(",")) {
            StringBuilder result = new StringBuilder();
            for (String c : cleanCity.split(",")) {
                result.append(getWeather(c.trim())).append("\n");
            }
            return result.toString().trim();
        }

        try {
            String encodedCity = URLEncoder.encode(cleanCity.trim(), StandardCharsets.UTF_8);
            String url = "https://wttr.in/" + encodedCity + "?format=3";

            return httpClient.get(url);

        } catch (Exception e) {
            return "Error fetching weather: " + e.getMessage();
        }
    }
}
