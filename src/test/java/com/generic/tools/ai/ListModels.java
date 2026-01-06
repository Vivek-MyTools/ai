package com.generic.tools.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ListModels {

    public static void main(String[] args) {
        // 1. Get the Key
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("❌ Error: GEMINI_API_KEY environment variable is not set.");
            return;
        }

        // 2. Build the URL for the List Models endpoint
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;

        // 3. Send Request
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            System.out.println("Querying Google AI for available models...");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 4. Output Results
            if (response.statusCode() == 200) {
                System.out.println("\n✅ AVAILABLE MODELS:");
                System.out.println(response.body());
                System.out.println("\n👉 INSTRUCTION: Pick a 'name' from above (e.g., 'models/gemini-pro'), REMOVE the 'models/' prefix, and use that in your code.");
            } else {
                System.err.println("❌ HTTP Error: " + response.statusCode());
                System.err.println(response.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}