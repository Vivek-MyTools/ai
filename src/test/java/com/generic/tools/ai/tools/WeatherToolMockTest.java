package com.generic.tools.ai.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class WeatherToolMockTest {

    @Mock
    HttpClientWrapper httpClient;

    private WeatherTool weatherTool;

    @BeforeEach
    void setUp() {
        weatherTool = new WeatherTool(httpClient); // 🔥 explicit injection
    }

    @Test
    void testGetWeather_OfflineSuccess() throws Exception {
        Mockito.when(httpClient.get(Mockito.anyString()))
               .thenReturn("London: ⛅️ +15°C");

        String result = weatherTool.getWeather("London");

        Assertions.assertEquals("London: ⛅️ +15°C", result);
    }
}
