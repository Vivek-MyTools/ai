package com.generic.tools.ai;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.OllamaHelper;
import com.generic.tools.ai.tools.CalculatorTool;
import com.generic.tools.ai.tools.RssNewsTool;
import com.generic.tools.ai.tools.StockTool;
import com.generic.tools.ai.tools.WeatherTool;
import com.generic.tools.ai.tools.WebHeaderTimeTool;

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.TokenWindowChatMemory; // 👈 NEW: Safer memory
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer; // 👈 NEW: For token counting
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class SuperAgentFactory {

    private static final Logger LOG = Logger.getLogger(SuperAgentFactory.class.getName());
    
    private final String baseUrl;
    private final String modelName;
    private final String apiKey; 
    
    // 🛑 FIX: Removed 'static'. Cache is now specific to this Factory instance.
    // This allows settings changes to take effect immediately.
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    
    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
    }
    // --- DEFAULT INSTRUCTIONS ---
    public static final String DEFAULT_SYSTEM_PROMPT = """
       You are a smart assistant with access to Internal Documents (RAG), a News Tool, a Weather Tool, a Calculator Tool, and your own general knowledge.
        
        CRITICAL RULES FOR TOOLS:
            1. When you use a tool (Weather, News, Time), the result comes from the SYSTEM, not the user.
            2. Do NOT get confused. If you see tool data in the history, it means YOU successfully found it.
            3. STOP immediately after receiving a tool result and answer the user.
            4. NEVER call the same tool twice for the same query.
                
        Tools:                                                                                  
            1. Calculator: MUST state the numeric result explicitly.
            2. Weather: Use the weather tool if asked about temperature/forecast.
            3. News: Use the RssNewsTool for latest headlines, world events, or specific topics.
            4. RAG: Search internal documents for specific entities (Project names, Codes).
            5. Fallback: If not found in docs, use general knowledge.
            6. Math: Output formulas as pure LaTeX (no dollar signs).
    """;

    public SuperAgentFactory(String baseUrl, String modelName) {
        this(baseUrl, modelName, null);
    }
    
    public SuperAgentFactory(String baseUrl, String modelName, String apiKey) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.apiKey = apiKey;
    }

    /**
     * Option 1: Create Agent with Default Prompt + Extra Tools
     */
    public Assistant createSuperAgent(String docFolderPath, Object... extraTools) {
        return createSuperAgent(docFolderPath, DEFAULT_SYSTEM_PROMPT, extraTools);
    }

    /**
     * Option 2: Create Agent with Custom Prompt + Extra Tools
     */
    public Assistant createSuperAgent(String docFolderPath, String systemInstruction, Object... extraTools) {
        LOG.info("--- 🦸 Initializing Super Agent (" + this.modelName + ") ---");
 
        ChatLanguageModel model = createLanguageModel();
        
        ContentRetriever retriever = setupRAG(docFolderPath);

        // --- MERGE TOOLS ---
        List<Object> allTools = new ArrayList<>();
        allTools.add(new CalculatorTool()); 
        allTools.add(new WeatherTool());    
        allTools.add(new StockTool());
        allTools.add(new WebHeaderTimeTool());
        allTools.add(new RssNewsTool());
        
        if (extraTools != null) {
            Collections.addAll(allTools, extraTools); 
        }
        
        boolean isLocal = modelName.toLowerCase().contains("local") || 
                  modelName.toLowerCase().contains("llama") ||
                  OllamaHelper.isOllamaAvailable();
        
        int memoryBudget = isLocal ? 4000 : 1000;

        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                
                // 🛑 FIX: Use TokenWindow to prevent 500 Context Errors
                // Keeps ~4000 tokens (approx 3000 words). Safe for file lists.
                .chatMemory(TokenWindowChatMemory.builder()
                        .maxTokens(memoryBudget, new OpenAiTokenizer("gpt-3.5-turbo")) 
                        .build())                
                .tools(allTools.toArray()) 
                .contentRetriever(retriever)
                .systemMessageProvider(chatMemoryId -> systemInstruction) 
                .build();
    }
    
    // --- HELPER 1: MODEL CREATION STRATEGY ---
    private ChatLanguageModel createLanguageModel() {
        String openAiKey = (this.apiKey != null) ? this.apiKey : System.getenv("OPENAI_API_KEY");
        String geminiKey = (this.apiKey != null) ? this.apiKey : System.getenv("GOOGLE_AI_GEMINI_API_KEY");

        boolean isGemini = modelName.toLowerCase().startsWith("gemini");
        boolean isOpenAI = modelName.toLowerCase().startsWith("gpt") 
                && !modelName.toLowerCase().contains("local")
                && !modelName.toLowerCase().contains("oss");

        LOG.info("Gemini => " + isGemini);
        LOG.info("OpenAI => " + isOpenAI);
        
        String cacheKey = isOpenAI ? "openai::" + modelName : 
                          isGemini ? "gemini::" + modelName : "ollama::" + modelName;

        return modelCache.computeIfAbsent(cacheKey, key -> {
            
            // --- PATH A: OPENAI (Official Cloud) ---
            if (isOpenAI) {
                System.out.println("☁️ Building OpenAI Model: " + this.modelName);
                if (openAiKey == null) throw new IllegalArgumentException("Missing OpenAI API Key");
                return OpenAiChatModel.builder()
                        .apiKey(openAiKey)
                        .modelName(this.modelName)
                        .temperature(0.0)
                        .timeout(Duration.ofSeconds(60))
                        .maxRetries(0) // 🛑 Disable retries for Stop button
                        .build();
            }

            // --- PATH B: GEMINI (Official Cloud) ---
            if (isGemini) {
                LOG.info("💎 Building Google Gemini Model: " + this.modelName);
                if (geminiKey == null) throw new IllegalArgumentException("Missing Gemini API Key");
                return GoogleAiGeminiChatModel.builder()
                        .apiKey(geminiKey)
                        .modelName(this.modelName)
                        .temperature(0.0)
                        .maxRetries(0)
                        .build();
            }

            // --- PATH C: HYBRID (Ollama First -> Cloud Fallback) ---
            LOG.info("⚡ Intent: Hybrid Execution (Ollama Priority) for: " + this.modelName);

            // 1. Try Local Ollama
            if (OllamaHelper.isOllamaAvailable()) {
                LOG.info("   ✅ Ollama is online. Using Local Inference.");
                return OllamaChatModel.builder()
                        .baseUrl("http://localhost:11434") 
                        .modelName(this.modelName)
                        .temperature(0.0)
                        
                        // 🛑 CRITICAL FIXES FOR OLLAMA:
                        .timeout(Duration.ofMinutes(5)) // Increase timeout (60s is too short for RAG)
                        .maxRetries(0) // Stops "Zombie Tasks" (RetryUtils error)
                        .numCtx(8192)  // Fixes "500 Internal Server Error" (Context Overflow)
                        
                        .build();
            }

            // 2. Ollama is Down -> Execute User's Chosen Model on Cloud
            LOG.info("   ⚠️ Ollama is OFFLINE. Switching to Cloud Fallback.");
            
            String cloudBaseUrl = (this.baseUrl != null && !this.baseUrl.contains("localhost")) 
                                  ? this.baseUrl 
                                  : "https://api.openai.com/v1"; 

            LOG.info("   👉 Target Endpoint: " + cloudBaseUrl);

            if (openAiKey == null) {
                 throw new RuntimeException("Ollama is offline and no API Key found for Cloud fallback.");
            }

            return OpenAiChatModel.builder()
                    .baseUrl(cloudBaseUrl) 
                    .apiKey(openAiKey)
                    .modelName(this.modelName)
                    .temperature(0.2)
                    .maxRetries(0) // 🛑 Disable retries
                    .timeout(Duration.ofMinutes(2))
                    .build();
        });
    }

    // --- HELPER 2: RAG SETUP ---
    private ContentRetriever setupRAG(String folderPath) {
        AllMiniLmL6V2EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (folderPath != null && !folderPath.isEmpty()) {
            Path docDir = Paths.get(folderPath);
            if (!Files.exists(docDir)) {
                System.out.println("❌ ERROR: Directory does not exist: " + docDir.toAbsolutePath());
                return createEmptyRetriever(embeddingStore, embeddingModel);
            }

            System.out.println("📂 Scanning folder (Non-Recursive): " + docDir.toAbsolutePath());
            
            String pattern = "glob:*.{pdf,docx,txt,json,java,js,css,PDF,DOCX,TXT,JSON}";
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
            List<dev.langchain4j.data.document.Document> allDocuments = new ArrayList<>();

            try (Stream<Path> stream = Files.list(docDir)) { 
                stream.filter(Files::isRegularFile).forEach(file -> {
                    if (matcher.matches(file.getFileName())) {
                        try {
                            System.out.println("   --> Loading: " + file.getFileName());
                            dev.langchain4j.data.document.Document doc;
                            String fileName = file.getFileName().toString().toLowerCase();

                            if (fileName.endsWith(".txt") || fileName.endsWith(".json") || 
                                fileName.endsWith(".java") || fileName.endsWith(".md")) {
                                doc = FileSystemDocumentLoader.loadDocument(file, new TextDocumentParser());
                            } else if (fileName.endsWith(".pdf")) {
                                doc = FileSystemDocumentLoader.loadDocument(file, new ApachePdfBoxDocumentParser());
                            } else {
                                doc = FileSystemDocumentLoader.loadDocument(file);
                            }
                            allDocuments.add(doc);
                        } catch (Exception e) {
                            System.err.println("   ❌ Failed to read: " + file.getFileName());
                        }
                    }
                });
            } catch (IOException e) {
                System.err.println("❌ Error listing directory: " + e.getMessage());
            }

            if (!allDocuments.isEmpty()) {
                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .documentSplitter(DocumentSplitters.recursive(300, 20))
                        .build();
                ingestor.ingest(allDocuments);
                System.out.println("🎉 Indexed " + allDocuments.size() + " files.");
            }
        }
        return createEmptyRetriever(embeddingStore, embeddingModel);
    }
    
    // --- HELPER 3: RETRIEVER CONFIGURATION ---
    private ContentRetriever createEmptyRetriever(InMemoryEmbeddingStore<TextSegment> store, AllMiniLmL6V2EmbeddingModel model) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(model)
                .maxResults(3) // 🛑 Limit results to save tokens
                .minScore(0.6)
                .build();
    }
}