package com.generic.tools.ai;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.OllamaHelper;
import com.generic.tools.ai.helper.OpenRouterHelper;
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
        
        // Smart Memory Budget: OpenRouter/Cloud models get generous context, local models default budget
        boolean isLocal = OllamaHelper.isOllamaAvailable() && OllamaHelper.isInstalledInOllama(modelName);
        int memoryBudget = isLocal ? 4000 : 8192;

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
            String resolvedName = OpenRouterHelper.resolveModelName(modelName);
            String normalizedName = resolvedName.toLowerCase();

            // 1. Local Ollama Check:
            // If local Ollama host is specified or local Ollama is running and model is installed locally, use local Ollama
            boolean isLocalOllamaHost = this.customBaseUrl != null && this.customBaseUrl.contains("localhost");
            boolean isInstalledLocallyInOllama = OllamaHelper.isOllamaAvailable() && OllamaHelper.isInstalledInOllama(resolvedName);
            
            if (isLocalOllamaHost || isInstalledLocallyInOllama) {
                String ollamaUrl = (this.customBaseUrl != null && !this.customBaseUrl.isBlank())
                        ? this.customBaseUrl
                        : OllamaHelper.getOllamaBaseUrl();
                LOG.info("🏠 Routing to Local Ollama model: " + resolvedName + " at " + ollamaUrl);
                return OllamaChatModel.builder()
                        .baseUrl(ollamaUrl)
                        .modelName(resolvedName)
                        .temperature(0.0)
                        .timeout(Duration.ofMinutes(5))
                        .numCtx(8192)
                        .build();
            }

            // 2. Google Gemini direct API check (if model starts with gemini without provider '/' and GEMINI key exists)
            if (normalizedName.startsWith("gemini") && !normalizedName.contains("/") &&
                    (this.apiKey != null || System.getenv("GOOGLE_AI_GEMINI_API_KEY") != null || System.getenv("GEMINI_API_KEY") != null)) {
                return createGeminiModel();
            }

            // 3. OpenRouter Provider (Default Cloud Provider)
            if (OpenRouterHelper.isOpenRouterModel(resolvedName) || OpenRouterHelper.getApiKey(this.apiKey) != null || normalizedName.contains("openrouter")) {
                return createOpenRouterModel(resolvedName);
            }

            // 4. OpenAI / Fallback
            return createOpenAiModel(resolveOpenAiBaseUrl());
        });
    }

    private ChatLanguageModel createOpenRouterModel(String targetModelName) {
        String effectiveKey = OpenRouterHelper.getApiKey(this.apiKey);
        if (effectiveKey == null || effectiveKey.isBlank()) {
            throw new RuntimeException("CRITICAL: OPENROUTER_API_KEY environment variable is missing or invalid.\n"
                    + "Please set OPENROUTER_API_KEY (OpenRouter keys start with 'sk-or-v1-').");
        }

        String targetUrl = (this.customBaseUrl != null && !this.customBaseUrl.isBlank())
                ? this.customBaseUrl
                : OpenRouterHelper.getOpenRouterBaseUrl();

        LOG.info("🌐 Creating OpenRouter Chat Model: " + targetModelName + " at " + targetUrl);

        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("HTTP-Referer", "https://github.com/generic-tools/ai");
        customHeaders.put("X-Title", "AI Assistant Panel");

        return OpenAiChatModel.builder()
                .baseUrl(targetUrl)
                .apiKey(effectiveKey)
                .modelName(targetModelName)
                .temperature(0.0)
                .timeout(Duration.ofMinutes(3))
                .maxRetries(1)
                .customHeaders(customHeaders)
                .build();
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
        String key = (this.apiKey != null) ? this.apiKey : (System.getenv("GOOGLE_AI_GEMINI_API_KEY") != null ? System.getenv("GOOGLE_AI_GEMINI_API_KEY") : System.getenv("GEMINI_API_KEY"));
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