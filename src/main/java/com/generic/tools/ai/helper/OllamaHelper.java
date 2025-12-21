package com.generic.tools.ai.helper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.HashSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OllamaHelper {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String TAGS_ENDPOINT = OLLAMA_BASE_URL + "/api/tags";

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Common Ollama / open-source model families
    private static final Set<String> KNOWN_OLLAMA_MODELS = Set.of(
            "llama", "llama2", "llama3",
            "mistral", "mixtral",
            "phi", "phi3",
            "qwen", "qwen2",
            "gemma",
            "codellama",
            "deepseek",
            "falcon",
            "orca",
            "yi"
    );

    private static Set<String> installedModelBases;

    private OllamaHelper() {}

    /** Is Ollama running locally */
    public static boolean isOllamaAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_BASE_URL))
                    .GET()
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Installed models only */
    private static Set<String> getInstalledModelBases() {
        if (installedModelBases != null) {
            return installedModelBases;
        }

        Set<String> bases = new HashSet<>();

        if (!isOllamaAvailable()) {
            return bases;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TAGS_ENDPOINT))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return bases;
            }

            JsonNode root = objectMapper.readTree(response.body());
            for (JsonNode model : root.get("models")) {
                bases.add(extractBaseName(model.get("name").asText()));
            }

            installedModelBases = bases;

        } catch (Exception ignored) {}

        return bases;
    }

    /** 
     * Main API: identify Ollama-origin model
     * Returns true EVEN if not installed
     */
    public static boolean isOllamaModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }

        String base = extractBaseName(modelName);

        // Installed locally
        if (getInstalledModelBases().contains(base)) {
            return true;
        }

        // Known Ollama ecosystem models
        return KNOWN_OLLAMA_MODELS.contains(base);
    }

    /** Optional: strict check */
    public static boolean isInstalledInOllama(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        return getInstalledModelBases().contains(extractBaseName(modelName));
    }

    private static String extractBaseName(String model) {
        return model.toLowerCase().split(":")[0];
    }
}
