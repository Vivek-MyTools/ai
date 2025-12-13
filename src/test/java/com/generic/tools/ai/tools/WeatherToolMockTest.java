package com.generic.tools.ai.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ExtendWith(MockitoExtension.class) // Initializes the @Mock objects
public class WeatherToolMockTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockResponse;

    @SuppressWarnings("unchecked")
	@Test
    public void testGetWeather_OfflineSuccess() throws IOException, InterruptedException {
        // --- 1. ARRANGE (Set up the fake scenario) ---
        
        // Define what the fake response should look like
        Mockito.when(mockResponse.statusCode()).thenReturn(200);
        Mockito.when(mockResponse.body()).thenReturn("London: ⛅️ +15°C");

        // Tell the fake Client: "When anyone sends ANY request, return the fake response"
        Mockito.when(mockHttpClient.send(
                ArgumentMatchers.any(HttpRequest.class), 
                ArgumentMatchers.any(HttpResponse.BodyHandler.class))
        ).thenReturn(mockResponse);

        // Create the tool using the FAKE client
        WeatherTool tool = new WeatherTool(mockHttpClient);

        // --- 2. ACT (Run the method) ---
        String result = tool.getWeather("London");

        // --- 3. ASSERT (Verify results) ---
        System.out.println("Mocked Result: " + result);
        
        Assertions.assertEquals("London: ⛅️ +15°C", result);
        
        // Verify that the tool actually tried to send a request exactly once
        Mockito.verify(mockHttpClient, Mockito.times(1))
               .send(ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}