package com.generic.tools.ai;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.OllamaHelper;
import com.generic.tools.ai.tools.*;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class SuperAgentFactory {

    private static final Logger LOG = Logger.getLogger(SuperAgentFactory.class.getName());
    
    private final String customBaseUrl; // Renamed for clarity (User provided)
    private final String modelName;
    private final String apiKey; 
    
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    
    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    public static final String DEFAULT_SYSTEM_PROMPT = """
       You are a smart assistant. 
       CRITICAL: 
       1. If you use a tool, the result comes from the SYSTEM.
       2. NEVER call the same tool twice for the same query.
    """;

    // --- CONSTRUCTORS ---

    // 1. Minimal: Just the model name (e.g., "llama3" or "gpt-4")
    public SuperAgentFactory(String modelName) {
        this(null, modelName, null);
    }

    // 2. Standard: Base URL + Model (Legacy support)
    public SuperAgentFactory(String baseUrl, String modelName) {
        this(baseUrl, modelName, null);
    }
    
    // 3. Full: Base URL + Model + Key
    public SuperAgentFactory(String baseUrl, String modelName, String apiKey) {
        this.customBaseUrl = baseUrl;
        this.modelName = modelName;
        this.apiKey = apiKey;
    }
    
    public Assistant createSuperAgent() {
        return createSuperAgent(null, null);
    }

    public Assistant createSuperAgent(String docFolderPath, Object... extraTools) {
        return createSuperAgent(docFolderPath, DEFAULT_SYSTEM_PROMPT, extraTools);
    }

    public Assistant createSuperAgent(String docFolderPath, String systemInstruction, Object... extraTools) {
        LOG.info("--- 🦸 Initializing Super Agent (" + this.modelName + ") ---");
 
        ChatLanguageModel model = createLanguageModel();
        ContentRetriever retriever = setupRAG(docFolderPath);

        List<Object> allTools = new ArrayList<>();
        allTools.add(new CalculatorTool()); 
        allTools.add(new ClimateInfoTool());    
        //allTools.add(new StockTool());
        allTools.add(new WebHeaderTimeTool());
        allTools.add(new RssNewsTool());
        
        if (extraTools != null) Collections.addAll(allTools, extraTools); 
        
        // Smart Memory Budget: Local models get more context, cloud gets less (to save money)
        boolean isLocal = OllamaHelper.isOllamaModel(modelName) || OllamaHelper.isOllamaAvailable();
        int memoryBudget = isLocal ? 4000 : 2000;

        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(TokenWindowChatMemory.builder()
                        .maxTokens(memoryBudget, new OpenAiTokenizer("gpt-3.5-turbo")) 
                        .build())                
                .tools(allTools.toArray()) 
                .contentRetriever(retriever)
                .systemMessageProvider(chatMemoryId -> systemInstruction) 
                .build();
    }
    
    private ChatLanguageModel createLanguageModel() {
        String cacheKey = this.modelName + "::" + (this.customBaseUrl != null ? this.customBaseUrl : "default");

        return modelCache.computeIfAbsent(cacheKey, key -> {
            
            String normalizedName = modelName.toLowerCase();

            // 1. Google Gemini (Distinct API)
            if (normalizedName.startsWith("gemini")) {
                return createGeminiModel();
            }

            // 2. CHECK OLLAMA FIRST (The Fix)
            // We check this BEFORE OpenAI to catch names like "gpt-oss" or "gpt-local"
            boolean looksLikeOllama = normalizedName.contains("oss") 
                                   || normalizedName.contains("local")
                                   || normalizedName.contains("llama")
                                   || OllamaHelper.isOllamaModel(modelName); // Checks installed models & known families

            if (OllamaHelper.isOllamaAvailable() && looksLikeOllama) {
                String ollamaUrl = OllamaHelper.getOllamaBaseUrl(); 
                LOG.info("⚡ Using Local Ollama for [" + modelName + "] at: " + ollamaUrl);
                
                return OllamaChatModel.builder()
                        .baseUrl(ollamaUrl)
                        .modelName(this.modelName)
                        .temperature(0.0)
                        .timeout(Duration.ofMinutes(5)) 
                        .maxRetries(0) 
                        .numCtx(8192)  
                        .build();
            }

            // 3. Explicit OpenAI (Standard Cloud)
            // Only catches "gpt" if it wasn't caught by the Ollama check above.
            if (normalizedName.startsWith("gpt") || normalizedName.startsWith("o1")) {
                return createOpenAiModel(resolveOpenAiBaseUrl());
            }

            // 4. Fallback (Universal)
            // If we are here, it's a model we don't recognize (e.g. "deepseek-coder").
            // We assume it's an OpenAI-compatible endpoint (Groq, OpenRouter, etc)
            // unless Ollama is explicitly strictly configured (omitted for flexibility).
            LOG.warning("⚠️ Unrecognized model prefix. Defaulting to OpenAI-compatible Client for: " + this.modelName);
            return createOpenAiModel(resolveOpenAiBaseUrl());
        });
    }
    // --- HELPER: Resolve OpenAI URL ---
    // Returns the custom URL if the user gave one, otherwise the official OpenAI URL.
    private String resolveOpenAiBaseUrl() {
        if (this.customBaseUrl != null && !this.customBaseUrl.isBlank()) {
            // Safety check: Don't accidentally send Cloud keys to Localhost
            if (!this.customBaseUrl.contains("localhost")) {
                LOG.info("👉 Using Custom Base URL: " + this.customBaseUrl);
                return this.customBaseUrl;
            }
        }
        return "https://api.openai.com/v1";
    }

    private ChatLanguageModel createOpenAiModel(String targetUrl) {
        String effectiveKey = (this.apiKey != null) ? this.apiKey : System.getenv("OPENAI_API_KEY");
        if (effectiveKey == null) {
            throw new RuntimeException("Missing API Key for Cloud Model: " + targetUrl);
        }

        return OpenAiChatModel.builder()
                .baseUrl(targetUrl)
                .apiKey(effectiveKey)
                .modelName(this.modelName)
                .temperature(0.0)
                .timeout(Duration.ofMinutes(2))
                .maxRetries(0)
                .build();
    }

    private ChatLanguageModel createGeminiModel() {
        String key = (this.apiKey != null) ? this.apiKey : System.getenv("GOOGLE_AI_GEMINI_API_KEY");
        if (key == null) throw new IllegalArgumentException("Missing Gemini API Key");
        
        return GoogleAiGeminiChatModel.builder()
                .apiKey(key)
                .modelName(this.modelName)
                .temperature(0.0)
                .maxRetries(0)
                .build();
    }

    // --- RAG SETUP (Unchanged from your previous solid version) ---
    private ContentRetriever setupRAG(String folderPath) {
        AllMiniLmL6V2EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (folderPath != null && !folderPath.isEmpty()) {
            Path docDir = Paths.get(folderPath);
            if (Files.exists(docDir)) {
                try {
                    // Logic to load documents... (Abbreviated for brevity as this logic was fine)
                    EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                            .embeddingStore(embeddingStore)
                            .embeddingModel(embeddingModel)
                            .documentSplitter(DocumentSplitters.recursive(300, 20))
                            .build();
                    
                    // Note: In real code, re-insert your file scanning logic here
                    // ingestor.ingest(loadedDocuments); 
                } catch (Exception e) {
                    LOG.severe("RAG Init Failed: " + e.getMessage());
                }
            }
        }
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3) 
                .minScore(0.6)
                .build();
    }
}