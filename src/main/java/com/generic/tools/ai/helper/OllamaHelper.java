package com.generic.tools.ai.helper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OllamaHelper {

    private static final Logger LOG = Logger.getLogger(OllamaHelper.class.getName());

    // ✅ CHANGED: Constants for fallback and Env Var only
    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    private static final String ENV_VAR_NAME = "OLLAMA_BASE_URL";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)) // Fail fast if offline
            .build();
            
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

    /**
     * ✅ NEW: Central source of truth for the URL.
     * Checks Environment Variable first, then defaults to localhost.
     */
    public static String getOllamaBaseUrl() {
        String envUrl = System.getenv(ENV_VAR_NAME);
        if (envUrl != null && !envUrl.isBlank()) {
            // Strip trailing slash to keep concatenation clean
            return envUrl.endsWith("/") ? envUrl.substring(0, envUrl.length() - 1) : envUrl;
        }
        return DEFAULT_OLLAMA_URL;
    }

    /** * Is Ollama running?
     * ✅ UPDATED: Uses dynamic URL
     */
    public static boolean isOllamaAvailable() {
        String url = getOllamaBaseUrl();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();
            
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            
            // 200 = OK. 404 is also "Available" (just means root has no content, but server is up)
            boolean available = (response.statusCode() == 200 || response.statusCode() == 404);
            
            if (available) {
                LOG.info("✅ Ollama detected at: " + url);
            }
            return available;
        } catch (Exception e) {
            LOG.fine("❌ Ollama not reachable at " + url + ": " + e.getMessage());
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
            // ✅ UPDATED: Construct the tags endpoint dynamically
            String tagsEndpoint = getOllamaBaseUrl() + "/api/tags";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tagsEndpoint))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return bases;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("models")) {
                for (JsonNode model : root.get("models")) {
                    bases.add(extractBaseName(model.get("name").asText()));
                }
            }
            installedModelBases = bases;

        } catch (Exception ignored) {
            // Logging ignored here to avoid spamming on recurring checks
        }

        return bases;
    }

    /** * Main API: identify Ollama-origin model
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
        // Handles "llama3:latest" -> "llama3"
        return model.toLowerCase().split(":")[0];
    }
}