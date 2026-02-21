package com.generic.tools.ai;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.OllamaHelper;
import com.generic.tools.ai.tools.*;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;

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
        
        int memoryBudget = 2000;          // non-Ollama (cloud) default
        if (isLocal) {
            // 3️⃣ Ollama models – map name → context window
            switch (modelName.toLowerCase()) {
                case "gpt-oss:20b-cloud":
                    memoryBudget = 8192;   // 8 192 tokens
                    break;
                case "qwen3-coder:480b-cloud":
                    memoryBudget = 32768;  // 32 768 tokens
                    break;
                // Add more Ollama models here as needed
                default:
                    memoryBudget = 4000;   // generic Ollama fallback
                    break;
            }
        }

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

        // 1. Google Gemini
        if (normalizedName.startsWith("gemini")) {
            return createGeminiModel();
        }

        // 2. OLLAMA (Local & Cloud)
        boolean isCloudModel = normalizedName.contains("-cloud");
        if (isCloudModel || normalizedName.contains("llama") || OllamaHelper.isOllamaModel(modelName)) {
            
            // URL handling to avoid the 404 /api/api/chat issue
            String ollamaUrl = (isCloudModel && !OllamaHelper.isOllamaAvailable()) 
                               ? "https://ollama.com" 
                               : OllamaHelper.getOllamaBaseUrl();

            // Fetch key from constructor or ENV
            String effectiveKey = (this.apiKey != null) ? this.apiKey : System.getenv("OLLAMA_API_KEY");
            LOG.info("Creating Agent using >>>>>OLLAMA_API_KEY !!<<<<<<");
            var builder = OllamaChatModel.builder()
                    .baseUrl(ollamaUrl)
                    .modelName(this.modelName)
                    .temperature(0.0)
                    .timeout(Duration.ofMinutes(5))
                    .numCtx(8192);

            // CRITICAL: This is what solves the 401 error
            if (effectiveKey != null && !effectiveKey.isBlank()) {
                builder.customHeaders(Collections.singletonMap("Authorization", "Bearer " + effectiveKey));
                LOG.info("🔐 Authorization header successfully injected for " + modelName);
            } else if (isCloudModel) {
                throw new RuntimeException("CRITICAL: OLLAMA_API_KEY is missing but required for cloud model.");
            }

            return builder.build();
        }

        // 3. OpenAI / Fallback
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
        // 1. Create the Embedding Model (process text into vectors)
        AllMiniLmL6V2EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        // 2. Create the Store (hold the vectors in memory)
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (folderPath != null && !folderPath.isEmpty()) {
            Path docDir = Paths.get(folderPath);

            if (Files.exists(docDir) && Files.isDirectory(docDir)) {
                LOG.info("📂 Scanning for documents in: " + docDir.toAbsolutePath());
                try {
                    // --- STEP A: Load the Files ---
                    // We use a glob path to recursively find files (e.g., "*.txt", "*.md", or "*" for all)
                    // Use ApacheTikaDocumentParser if you want to support PDF, DOCX, etc.
                    // Use TextDocumentParser if you only have .txt files.
                    List<Document> documents = FileSystemDocumentLoader.loadDocuments(
                            docDir,
                            new ApacheTikaDocumentParser() // Or new TextDocumentParser()
                    );

                    if (documents.isEmpty()) {
                        LOG.warning("⚠️ No documents found in folder: " + folderPath);
                    } else {
                        // --- STEP B: Configure the Ingestor ---
                        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                                .embeddingStore(embeddingStore)
                                .embeddingModel(embeddingModel)
                                .documentSplitter(DocumentSplitters.recursive(500, 50)) // Sized for context windows
                                .build();

                        // --- STEP C: Actually Ingest (CRITICAL STEP) ---
                        ingestor.ingest(documents);

                        LOG.info("✅ RAG Ready: Ingested " + documents.size() + " documents into memory.");
                    }

                } catch (Exception e) {
                    LOG.severe("❌ RAG Ingestion Failed: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                LOG.warning("⚠️ Document folder not found: " + folderPath);
            }
        }

        // 3. Create the Retriever (fetches relevant bits for the LLM)
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3) // How many snippets to feed the LLM
                .minScore(0.5) // Relevance threshold (0.0 to 1.0)
                .build();
    }

}