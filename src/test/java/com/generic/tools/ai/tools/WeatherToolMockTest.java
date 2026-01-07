package com.generic.tools.ai.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
class WeatherToolMockTest {

    @Mock
    HttpClientWrapper httpClient;

    private WeatherTool weatherTool;

    @BeforeEach
    void setUp() throws Exception {
        weatherTool = new WeatherTool();

        // Inject the mocked httpClient using reflection
        var field = WeatherTool.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(weatherTool, httpClient);
    }

    @Test
    void testGetWeather_MultipleCitiesOffline() throws Exception {
        Mockito.when(httpClient.get(Mockito.anyString()))
               .thenAnswer(invocation -> {
                   String url = invocation.getArgument(0, String.class);
                   if (url.contains("Delhi")) {
                       return "Delhi: ☀️ +15°C";
                   } else if (url.contains("Kochi")) {
                       return "Kochi: 🌤 +28°C";
                   } else if (url.contains("Bangalore") || url.contains("blr")) {
                       return "Bangalore: ☀️ +22°C";
                   } else if (url.contains("Tokyo")) {
                       return "Tokyo: ❄️ +5°C";
                   }
                   return "Unknown location";
               });

        List<String> cities = List.of("Kochi", "Bangalore", "Delhi", "Tokyo");
        List<String> results = cities.stream()
                .map(weatherTool::getWeather)
                .collect(Collectors.toList());

        String output = "**Current temperatures**\n\n" +
                results.stream()
                        .map(r -> "- " + r.replaceAll("^[^:]+: ", "**$0:** "))
                        .collect(Collectors.joining("\n"));

        System.out.println(output);

        Assertions.assertTrue(results.get(0).contains("Kochi"));
        Assertions.assertTrue(results.get(1).contains("Bangalore"));
        Assertions.assertTrue(results.get(2).contains("Delhi"));
        Assertions.assertTrue(results.get(3).contains("Tokyo"));
    }
}
