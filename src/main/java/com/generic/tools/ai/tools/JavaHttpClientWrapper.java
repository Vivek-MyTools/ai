package com.generic.tools.ai.tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class JavaHttpClientWrapper implements HttpClientWrapper {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // ✅ REQUIRED for wttr.in
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    @Override
    public String get(String url) throws Exception {
        Exception last = null;

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "curl/8.0.0")
                        .GET()
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body().trim();
                }
                return "Error: Status " + response.statusCode();

            } catch (Exception e) {
                last = e;
            }
        }

        throw last;
    }
}
