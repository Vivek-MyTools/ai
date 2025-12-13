package com.generic.tools.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class WeatherTool {

    private final HttpClient httpClient;

    // 1. Default Constructor (Used by the Main App)
    public WeatherTool() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    // 2. Testing Constructor (Used by Tests to inject a fake client)
    public WeatherTool(HttpClient httpClient) {
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
    	System.out.println("Using Tool: Weather lookup for " + city );
    	// 1. Sanitize Input: Remove brackets and quotes if the model messes up
        String cleanCity = city.replace("[", "").replace("]", "").replace("\"", "");

        // 2. Handle simple Comma separation (Recursive fix)
        if (cleanCity.contains(",")) {
            String[] cities = cleanCity.split(",");
            StringBuilder result = new StringBuilder();
            for (String c : cities) {
                result.append(getWeather(c.trim())).append("\n"); // Recursive call
            }
            return result.toString();
        }
    	
        if (city == null || city.isBlank()) return "Error: No city name provided.";

        try {
            String encodedCity = URLEncoder.encode(city.trim(), StandardCharsets.UTF_8);
            String url = "https://wttr.in/" + encodedCity + "?format=3";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            // The tool uses whichever client was passed to the constructor
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body().trim();
            } else {
                return "Error: Status " + response.statusCode();
            }
        } catch (Exception e) {
            return "Error fetching weather: " + e.getMessage();
        }
    }
}