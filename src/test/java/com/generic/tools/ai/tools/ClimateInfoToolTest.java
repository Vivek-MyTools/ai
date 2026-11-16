package com.generic.tools.ai.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClimateInfoToolTest {

    @Test
    void testGetWeatherLive() {
        ClimateInfoTool tool = new ClimateInfoTool();
        String result = tool.getWeather("Delhi");
        System.out.println("Result for Delhi: " + result);
        assertNotNull(result);
        assertFalse(result.contains("Weather unavailable"), "Weather for Delhi should be available but got: " + result);
    }
}
