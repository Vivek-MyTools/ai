package com.generic.tools.ai.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

public class WeatherToolTest {

    @Test
    @DisplayName("Should return weather data for a valid city")
    public void testGetWeather_ValidCity() {
        // 1. Arrange
        WeatherTool tool = new WeatherTool();
        String city = "London";

        // 2. Act
        String result = tool.getWeather(city);
        
        // 3. Assert (Verification)
        System.out.println("Test Output: " + result); // Print it so you can see it
        
        Assertions.assertNotNull(result, "Result should not be null");
        
        // wttr.in usually returns the city name in the response, e.g., "London: ⛅️ +15°C"
        // We check if the response contains the city name to verify accuracy.
        Assertions.assertTrue(result.contains("London"), 
            "Response should contain the city name");
            
        // Ensure it is not an error message
        Assertions.assertFalse(result.startsWith("Error"), 
            "Response should not start with Error");
    }

    @Test
    @DisplayName("Should handle invalid cities gracefully")
    public void testGetWeather_InvalidCity() {
        // 1. Arrange
        WeatherTool tool = new WeatherTool();
        String fakeCity = "NotARealCity12345";

        // 2. Act
        String result = tool.getWeather(fakeCity);
        
        // 3. Assert
        System.out.println("Test Output: " + result);
        
        // Your code is designed to return "Error: City ... not found" on 404
        Assertions.assertTrue(result.contains("Error"), 
            "Should return an error message for invalid cities");
    }
}