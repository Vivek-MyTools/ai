package com.generic.tools.ai.tools;
import dev.langchain4j.agent.tool.Tool;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
/**
 *
 * @author vipa0816
 */
public class ClimateInfoTool {
     private static final String GEOCODE_URL =
            "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1";

    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true";

    private static final Pattern CITY_SPLITTER =
            Pattern.compile(",|\\band\\b", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    public ClimateInfoTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Tool("""
        Returns current weather conditions for cities using wttr.in.
        
        Guidelines:
        - You can accept multiple city names separated by commas or 'and'.
        - The agent should split cities and call this tool separately for each city.
        - Returns a simple summary with emoji, temperature (°C), and basic condition.
        
        Example input: "Kochi"
        Example output: "Kochi: 🌤 +28°C"
        """)
    public String getWeather(String cities) {
        List<String> cityList = splitCities(cities);
        List<String> results = new ArrayList<>();

        for (String city : cityList) {
            results.add(getWeatherForCity(city));
        }

        return String.join("\n", results);
    }

    private List<String> splitCities(String cities) {
        String[] parts = CITY_SPLITTER.split(cities);
        List<String> list = new ArrayList<>();

        for (String part : parts) {
            String city = part.trim();
            if (!city.isEmpty()) {
                list.add(city);
            }
        }
        return list;
    }

    private String getWeatherForCity(String city) {
        try {
            // 1. Geocode city → lat/lon
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String geoUrl = String.format(GEOCODE_URL, encodedCity);

            HttpRequest geoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(geoUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> geoResponse =
                    httpClient.send(geoRequest, HttpResponse.BodyHandlers.ofString());

            JSONObject geoJson = new JSONObject(geoResponse.body());
            JSONArray results = geoJson.optJSONArray("results");

            if (results == null || results.isEmpty()) {
                return city + ": ❌ City not found";
            }

            JSONObject location = results.getJSONObject(0);
            double latitude = location.getDouble("latitude");
            double longitude = location.getDouble("longitude");

            // 2. Fetch current weather
            String weatherUrl = String.format(WEATHER_URL, latitude, longitude);

            HttpRequest weatherRequest = HttpRequest.newBuilder()
                    .uri(URI.create(weatherUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> weatherResponse =
                    httpClient.send(weatherRequest, HttpResponse.BodyHandlers.ofString());

            JSONObject weatherJson = new JSONObject(weatherResponse.body());
            JSONObject current = weatherJson.getJSONObject("current_weather");

            double temperature = current.getDouble("temperature");
            int weatherCode = current.getInt("weathercode");

            String emoji = mapWeatherCodeToEmoji(weatherCode);

            return city + ": " + emoji + " +" + Math.round(temperature) + "°C";

        } catch (Exception e) {
            return city + ": ❌ Weather unavailable";
        }
    }

    /**
     * Open-Meteo weather code mapping
     */
    private String mapWeatherCodeToEmoji(int code) {
        if (code == 0) return "☀️";               // Clear
        if (code <= 2) return "🌤";               // Mainly clear
        if (code == 3) return "☁️";               // Cloudy
        if (code >= 45 && code <= 48) return "🌫";
        if (code >= 51 && code <= 67) return "🌧";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 80 && code <= 82) return "🌧";
        if (code >= 95) return "⛈";

        return "🌡";
    }
}
