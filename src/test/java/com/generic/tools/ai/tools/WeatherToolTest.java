package com.generic.tools.ai.tools;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherToolTest {

    @Test
    void testGetWeather_ValidCity_ReturnsWeather() throws IOException, InterruptedException {
        // 1. Create Mocks (The "Fake" Objects)
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        // 2. Train the Mocks (Scripting the behavior)
        // Rule A: When we ask for the status code, return 200 (OK)
        when(mockResponse.statusCode()).thenReturn(200);
        // Rule B: When we ask for the body, return this fake weather string
        when(mockResponse.body()).thenReturn("London: ☀️ +15°C");
        
        // Rule C: When the client sends ANY request, return our fake response
        when(mockHttpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(mockResponse);

        // 3. Inject the Mock into your Tool
        WeatherTool tool = new WeatherTool(mockHttpClient);

        // 4. Run the Test
        String result = tool.getWeather("London");

        // 5. Verify
        System.out.println("Test Output: " + result);
        assertEquals("London: ☀️ +15°C", result);
    }

    @Test
    void testGetWeather_NetworkError_ReturnsErrorMessage() throws IOException, InterruptedException {
        // 1. Create Mock
        HttpClient mockHttpClient = mock(HttpClient.class);

        // 2. Train it to crash (Simulate Offline/Timeout)
        when(mockHttpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new IOException("Network unavailable"));

        // 3. Inject
        WeatherTool tool = new WeatherTool(mockHttpClient);

        // 4. Run
        String result = tool.getWeather("Paris");

        // 5. Verify it caught the exception gracefully
        System.out.println("Test Output: " + result);
        assertTrue(result.contains("Error fetching weather"));
        assertTrue(result.contains("Network unavailable"));
    }
    
    @Test
    void testGetWeather_RecursiveCommaSplit() throws IOException, InterruptedException {
        // This test verifies your comma-separation logic
        // We reuse the valid mock setup
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Sunny 25C");
        when(mockHttpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(mockResponse);

        WeatherTool tool = new WeatherTool(mockHttpClient);

        // Input with commas
        String result = tool.getWeather("London, Paris");
        
        // Should have called the logic twice (output contains two lines)
        assertTrue(result.contains("Sunny 25C"));
        // Count how many times "Sunny" appears (should be 2)
        int count = result.split("Sunny").length - 1;
        assertTrue(count >= 2, "Should return weather for both cities");
    }
}