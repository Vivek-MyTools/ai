package com.generic.tools.ai;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.ChatMessageData; // Import your Data Object
import com.generic.tools.ai.helper.OllamaHelper;
import com.generic.tools.ai.tools.*;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;   // Import for loading history
import dev.langchain4j.data.message.UserMessage; // Import for loading history
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;        // Import generic ChatMemory interface
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
    
    private final String customBaseUrl;
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

    public SuperAgentFactory(String modelName) {
        this(null, modelName, null);
    }

    public SuperAgentFactory(String baseUrl, String modelName) {
        this(baseUrl, modelName, null);
    }
    
    public SuperAgentFactory(String baseUrl, String modelName, String apiKey) {
        this.customBaseUrl = baseUrl;
        this.modelName = modelName;
        this.apiKey = apiKey;
    }
    
    // 1. Basic creation
    public Assistant createSuperAgent() {
        return createSuperAgent(null, DEFAULT_SYSTEM_PROMPT, null, null);
    }

    // 2. Creation with Docs and Tools (No History)
    public Assistant createSuperAgent(String docFolderPath, Object... extraTools) {
        return createSuperAgent(docFolderPath, DEFAULT_SYSTEM_PROMPT, extraTools, null);
    }

    // 3. Creation with Docs, System Prompt, and Tools (No History)
    public Assistant createSuperAgent(String docFolderPath, String systemInstruction, Object... extraTools) {
        return createSuperAgent(docFolderPath, systemInstruction, extraTools, null);
    }

    // 4. MAIN CREATION METHOD (Now accepts History)
    public Assistant createSuperAgent(String docFolderPath, String systemInstruction, Object[] extraTools, List<ChatMessageData> history) {
        LOG.info("--- 🦸 Initializing Super Agent (" + this.modelName + ") with History ---");
 
        ChatLanguageModel model = createLanguageModel();
        ContentRetriever retriever = setupRAG(docFolderPath);

        List<Object> allTools = new ArrayList<>();
        allTools.add(new CalculatorTool()); 
        allTools.add(new ClimateInfoTool());    
        //allTools.add(new StockTool());
        allTools.add(new WebHeaderTimeTool());
        allTools.add(new RssNewsTool());
        
        if (extraTools != null) Collections.addAll(allTools, extraTools); 
        
        // --- MEMORY SETUP ---
        boolean isLocal = OllamaHelper.isOllamaModel(modelName) || OllamaHelper.isOllamaAvailable();
        int memoryBudget = 2000;
        
        if (isLocal) {
            switch (modelName.toLowerCase()) {
                case "gpt-oss:20b-cloud": memoryBudget = 8192; break;
                case "qwen3-coder:480b-cloud": memoryBudget = 32768; break;
                default: memoryBudget = 4000; break;
            }
        }

        // 1. Create the Memory Object Manually
        ChatMemory chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(memoryBudget, new OpenAiTokenizer("gpt-3.5-turbo")) 
                .build();

        // 2. Populate Memory from History (Context Restoration) 
        if (history != null && !history.isEmpty()) {
            LOG.info("Loading " + history.size() + " messages into agent memory...");
            for (ChatMessageData msg : history) {
                if ("USER".equals(msg.role)) {
                    chatMemory.add(UserMessage.from(msg.content));
                } else if ("AGENT".equals(msg.role)) {
                    chatMemory.add(AiMessage.from(msg.content));
                }
            }
        }

        // 3. Build Service using the pre-filled memory
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory) // Pass the specific instance we populated
                .tools(allTools.toArray()) 
                .contentRetriever(retriever)
                .systemMessageProvider(chatMemoryId -> systemInstruction) 
                .build();
    }
    
    private ChatLanguageModel createLanguageModel() {
        String cacheKey = this.modelName + "::" + (this.customBaseUrl != null ? this.customBaseUrl : "default");

        return modelCache.computeIfAbsent(cacheKey, key -> {
            
            String normalizedName = modelName.toLowerCase();

            if (normalizedName.startsWith("gemini")) {
                return createGeminiModel();
            }

            boolean looksLikeOllama = normalizedName.contains("oss") 
                                   || normalizedName.contains("local")
                                   || normalizedName.contains("llama")
                                   || OllamaHelper.isOllamaModel(modelName);

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

            if (normalizedName.startsWith("gpt") || normalizedName.startsWith("o1")) {
                return createOpenAiModel(resolveOpenAiBaseUrl());
            }

            LOG.warning("⚠️ Unrecognized model prefix. Defaulting to OpenAI-compatible Client for: " + this.modelName);
            return createOpenAiModel(resolveOpenAiBaseUrl());
        });
    }

    private String resolveOpenAiBaseUrl() {
        if (this.customBaseUrl != null && !this.customBaseUrl.isBlank()) {
            if (!this.customBaseUrl.contains("localhost")) {
                LOG.info("👉 Using Custom Base URL: " + this.customBaseUrl);
                return this.customBaseUrl;
            }
        }
        return "https://api.openai.com/v1";
    }

    private ChatLanguageModel createOpenAiModel(String targetUrl) {
        LOG.info("Getting OPENAI_API_KEY");
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

    private ContentRetriever setupRAG(String folderPath) {
        AllMiniLmL6V2EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (folderPath != null && !folderPath.isEmpty()) {
            Path docDir = Paths.get(folderPath);
            if (Files.exists(docDir)) {
                try {
                    EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                            .embeddingStore(embeddingStore)
                            .embeddingModel(embeddingModel)
                            .documentSplitter(DocumentSplitters.recursive(300, 20))
                            .build();
                    
                    // Ingest documents here if needed
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