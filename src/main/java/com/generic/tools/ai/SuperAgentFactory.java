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
import java.util.stream.Stream;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.OllamaHelper;
import com.generic.tools.ai.tools.CalculatorTool;
import com.generic.tools.ai.tools.NtpTimeTool;
import com.generic.tools.ai.tools.RssNewsTool;
import com.generic.tools.ai.tools.StockTool;
import com.generic.tools.ai.tools.WeatherTool;
import com.generic.tools.ai.tools.WebHeaderTimeTool;

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class SuperAgentFactory {

    private final String baseUrl;
    private final String modelName;
    private final String apiKey; 
    
    private static final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    // --- DEFAULT INSTRUCTIONS ---
    public static final String DEFAULT_SYSTEM_PROMPT = """
	   You are a smart assistant with access to Internal Documents (RAG), a Weather Tool, a Calculator Tool, and your own general knowledge.
	    
	    1. Calculator: MUST state the numeric result explicitly.
	    2. Weather: Use the weather tool if asked about temperature/forecast.
	    3. RAG: Search internal documents for specific entities (Project names, Codes).
	    4. Fallback: If not found in docs, use general knowledge.
	    5. Math: Output formulas as pure LaTeX (no dollar signs).
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
        System.out.println("--- 🦸 Initializing Super Agent (" + this.modelName + ") ---");
 
        ChatLanguageModel model = createLanguageModel();
        
        // THIS CALLS THE METHOD AT THE BOTTOM OF THE FILE
        ContentRetriever retriever = setupRAG(docFolderPath);

        // --- MERGE TOOLS ---
        List<Object> allTools = new ArrayList<>();
        allTools.add(new CalculatorTool()); // Always add Calculator
        allTools.add(new WeatherTool());    // Always add Weather
        allTools.add(new StockTool());
        //allTools.add(new NtpTimeTool());
        allTools.add(new WebHeaderTimeTool());
        allTools.add(new RssNewsTool());
        
        if (extraTools != null) {
            Collections.addAll(allTools, extraTools); // Add user tools
        }

        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(allTools.toArray()) 
                .contentRetriever(retriever)
                .systemMessageProvider(chatMemoryId -> systemInstruction) 
                .build();
    }
    
    // --- HELPER 1: MODEL CREATION ---
    private ChatLanguageModel createLanguageModel() {
         String apiKeyForCache = (this.apiKey != null) ? this.apiKey : "env";
         String cacheKey = this.modelName + "::" + apiKeyForCache;
         
         return modelCache.computeIfAbsent(cacheKey, key -> {
            if(OllamaHelper.isOllamaAvailable() && OllamaHelper.isOllamaModel(modelName)) {
                System.out.println("⚡ Building Local Ollama Model: " + this.modelName);
                return OllamaChatModel.builder()
                        .baseUrl(this.baseUrl != null ? this.baseUrl : "http://localhost:11434")
                        .modelName(this.modelName)
                        .temperature(0.0)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            } else if (modelName.toLowerCase().startsWith("gemini")) {
                System.out.println("💎 Building Google Gemini Model: " + this.modelName);
                String finalKey = (this.apiKey != null) ? this.apiKey : System.getenv("GOOGLE_AI_GEMINI_API_KEY");
                return GoogleAiGeminiChatModel.builder()
                        .apiKey(finalKey).modelName(this.modelName).temperature(0.0).build();
            } else {
                System.out.println("☁️ Building OpenAI Model: " + this.modelName);
                String finalKey = (this.apiKey != null) ? this.apiKey : System.getenv("OPENAI_API_KEY");
                return OpenAiChatModel.builder()
                        .apiKey(finalKey).modelName(this.modelName).temperature(0.0).timeout(Duration.ofSeconds(60)).build();
            }
        });
    }

    // --- HELPER 2: RAG SETUP (THIS WAS MISSING) ---
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
            
            // Pattern includes JSON now
            String pattern = "glob:*.{pdf,docx,txt,json,java,js,css,PDF,DOCX,TXT,JSON}";
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
            List<dev.langchain4j.data.document.Document> allDocuments = new ArrayList<>();

            try (Stream<Path> stream = Files.list(docDir)) { // Files.list = Non-Recursive
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
    
    // --- HELPER 3: EMPTY RETRIEVER ---
    private ContentRetriever createEmptyRetriever(InMemoryEmbeddingStore<TextSegment> store, AllMiniLmL6V2EmbeddingModel model) {
        return EmbeddingStoreContentRetriever.builder().embeddingStore(store).embeddingModel(model).maxResults(3).minScore(0.6).build();
    }
}