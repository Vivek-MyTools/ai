package com.generic.tools.ai.helper;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class OpenRouterHelper {

    public static final String DEFAULT_OPENROUTER_URL = "https://openrouter.ai/api/v1";
    public static final String ENV_VAR_URL = "OPENROUTER_BASE_URL";
    public static final String ENV_VAR_KEY = "OPENROUTER_API_KEY";

    private OpenRouterHelper() {}

    public static String getOpenRouterBaseUrl() {
        String envUrl = System.getenv(ENV_VAR_URL);
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl.endsWith("/") ? envUrl.substring(0, envUrl.length() - 1) : envUrl;
        }
        return DEFAULT_OPENROUTER_URL;
    }

    public static String getApiKey(String explicitKey) {
        if (explicitKey != null && !explicitKey.isBlank()) {
            return explicitKey;
        }
        
        // 1. Standard Java Environment Variable
        String openRouterKey = System.getenv(ENV_VAR_KEY);
        if (openRouterKey != null && !openRouterKey.isBlank()) {
            return openRouterKey;
        }
        String altKey = System.getenv("OPENROUTER_KEY");
        if (altKey != null && !altKey.isBlank()) {
            return altKey;
        }

        // 2. System Property check (-DOPENROUTER_API_KEY=...)
        String sysProp = System.getProperty(ENV_VAR_KEY);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }

        // 3. On Windows: Read directly from Registry if JVM environment block was stale
        String osName = System.getProperty("os.name");
        if (osName != null && osName.toLowerCase().contains("win")) {
            String registryKey = readWindowsUserEnv(ENV_VAR_KEY);
            if (registryKey != null && !registryKey.isBlank()) {
                return registryKey;
            }
            String registryKeyAlt = readWindowsUserEnv("OPENROUTER_KEY");
            if (registryKeyAlt != null && !registryKeyAlt.isBlank()) {
                return registryKeyAlt;
            }
        }

        // 4. Fallback check for OPENAI_API_KEY if starting with sk-or-
        String openAiKey = System.getenv("OPENAI_API_KEY");
        if (openAiKey != null && openAiKey.startsWith("sk-or-")) {
            return openAiKey;
        }

        return null;
    }

    private static String readWindowsUserEnv(String varName) {
        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", varName).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(varName)) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 3) {
                            return parts[parts.length - 1];
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean isOpenRouterModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String lower = modelName.toLowerCase();
        return lower.contains("/") ||
               lower.contains("openrouter") ||
               lower.endsWith("-cloud") ||
               lower.startsWith("meta-llama") ||
               lower.startsWith("google/") ||
               lower.startsWith("deepseek/") ||
               lower.startsWith("qwen/") ||
               lower.startsWith("mistralai/") ||
               lower.startsWith("openai/") ||
               lower.startsWith("anthropic/");
    }

    public static String resolveModelName(String modelName) {
        if (modelName == null) return "meta-llama/llama-3.3-70b-instruct";
        String trimmed = modelName.trim();
        if (trimmed.toLowerCase().endsWith(":free")) {
            trimmed = trimmed.substring(0, trimmed.length() - 5);
        }
        return switch (trimmed.toLowerCase()) {
            case "gpt-oss:20b-cloud" -> "meta-llama/llama-3.3-70b-instruct";
            case "qwen3-coder:480b-cloud" -> "qwen/qwen-2.5-coder-32b-instruct";
            default -> trimmed;
        };
    }
}
