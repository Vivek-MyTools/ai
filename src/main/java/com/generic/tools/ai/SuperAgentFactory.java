package com.generic.tools.ai;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.helper.OllamaHelper;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel; // <--- IMPORANT IMPORT
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class SuperAgentFactory {

    private final String baseUrl;
    private final String modelName;
    private final String apiKey; 
    
    // Cache to reuse model instances (Singleton per model configuration)
    private static final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    // Constructor 1: Local Ollama (No API Key)
    public SuperAgentFactory(String baseUrl, String modelName) {
        this(baseUrl, modelName, null);
    }
    
    // Constructor 2: OpenAI or Custom (With API Key)
    public SuperAgentFactory(String baseUrl, String modelName, String apiKey) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.apiKey = apiKey;
    }
    
    // Overload for Chat-Only (No Tools)
    public Assistant createSuperAgent(String docFolderPath){
         ChatLanguageModel model = createLanguageModel();
         ContentRetriever retriever = setupRAG(docFolderPath);
         return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))                
                .contentRetriever(retriever)
                .build();
    }

    // Overload for Agents with Tools
    public Assistant createSuperAgent(String docFolderPath, Object... tools) {
        System.out.println("--- 🦸 Initializing Super Agent (" + this.modelName + ") ---");
 
        ChatLanguageModel model = createLanguageModel();
        
        ContentRetriever retriever = setupRAG(docFolderPath);

        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tools)
                .contentRetriever(retriever)
                .build();
    }
    
    /**
     * SMART MODEL FACTORY:
     * Switches between Ollama and OpenAI based on the model name.
     */
   private ChatLanguageModel createLanguageModel() {
         String apiKeyForCache = (this.apiKey != null) ? this.apiKey : "env";
         String cacheKey = this.modelName + "::" + apiKeyForCache;
         
         return modelCache.computeIfAbsent(cacheKey, key -> {
            
            // 1. Local Ollama
            if(OllamaHelper.isOllamaAvailable() && OllamaHelper.isOllamaModel(modelName)) {
                System.out.println("⚡ Building Local Ollama Model: " + this.modelName);
                return OllamaChatModel.builder()
                        .baseUrl(this.baseUrl != null ? this.baseUrl : "http://localhost:11434")
                        .modelName(this.modelName)
                        .temperature(0.0)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            }
            
            // 2. Google Gemini (NEW)
            else if (modelName.toLowerCase().startsWith("gemini")) {
                System.out.println("💎 Building Google Gemini Model: " + this.modelName);
                
                String finalKey = getApiKeyOrThrow("GOOGLE_AI_GEMINI_API_KEY");
                
                return GoogleAiGeminiChatModel.builder()
                        .apiKey(finalKey)
                        .modelName(this.modelName) // e.g., "gemini-1.5-pro"
                        .temperature(0.0)
                        .build();
            }
            
            // 3. OpenAI (Default Fallback for 'gpt')
            else {
                System.out.println("☁️ Building OpenAI Model: " + this.modelName);
                String finalKey = getApiKeyOrThrow("OPENAI_API_KEY");

                return OpenAiChatModel.builder()
                        .apiKey(finalKey)
                        .modelName(this.modelName)
                        .temperature(0.0)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            }
        });
    }

    // Helper to keep code clean
    private String getApiKeyOrThrow(String envVarName) {
        if (this.apiKey != null && !this.apiKey.isEmpty()) return this.apiKey;
        
        System.out.println("   (Checking Env Var: " + envVarName + "...)");
        String envKey = System.getenv(envVarName);
        
        if (envKey == null || envKey.isEmpty()) {
            throw new IllegalArgumentException("❌ API Key missing for " + this.modelName 
                + ". Please set " + envVarName + " or pass it to the application.");
        }
        return envKey;
    }

    private ContentRetriever setupRAG(String folderPath) {
        AllMiniLmL6V2EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        if (folderPath != null && !folderPath.isEmpty()) {
            Path docDir = Paths.get(folderPath);

            if (!Files.exists(docDir)) {
                System.out.println("❌ ERROR: Directory does not exist: " + docDir.toAbsolutePath());
                return createEmptyRetriever(embeddingStore, embeddingModel);
            }

            System.out.println("📂 Scanning folder: " + docDir.toAbsolutePath());

            // 1. Define Pattern (Recursive + Case Insensitive)
            String pattern = "glob:**/*.{pdf,docx,txt,java,js,css,PDF,DOCX,TXT}";
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);

            List<dev.langchain4j.data.document.Document> allDocuments = new ArrayList<>();

            // 2. MANUALLY collect files
            try (Stream<Path> stream = Files.walk(docDir)) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    if (matcher.matches(file)) {
                        try {
                            System.out.println("   --> Loading: " + file.getFileName());

                            dev.langchain4j.data.document.Document doc;
                            String fileName = file.getFileName().toString().toLowerCase();

                            // Choose parser based on extension
                            if (fileName.endsWith(".txt") || fileName.endsWith(".java") || fileName.endsWith(".md") || fileName.endsWith(".js") || fileName.endsWith(".css")) {
                                doc = FileSystemDocumentLoader.loadDocument(file, new TextDocumentParser());
                            } 
                            else if (fileName.endsWith(".pdf")) {
                                doc = FileSystemDocumentLoader.loadDocument(file, new ApachePdfBoxDocumentParser());
                            } 
                            else {
                                doc = FileSystemDocumentLoader.loadDocument(file);
                            }

                            allDocuments.add(doc);

                        } catch (Exception e) {
                            System.err.println("   ❌ Failed to read file: " + file.getFileName());
                            if (e.getMessage() != null && e.getMessage().contains("Access is denied")) {
                                System.err.println("      💡 HINT: This might be a OneDrive 'Cloud-only' file.");
                            }
                        }
                    }
                });
            } catch (IOException e) {
                System.err.println("❌ Error walking directory: " + e.getMessage());
            }

            // 3. Ingest found documents
            if (!allDocuments.isEmpty()) {
                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .documentSplitter(DocumentSplitters.recursive(300, 20))
                        .build();
                ingestor.ingest(allDocuments);
                System.out.println("🎉 Successfully Indexed " + allDocuments.size() + " files.");
            } else {
                System.out.println("⚠️ No valid documents loaded.");
            }
        }

        return createEmptyRetriever(embeddingStore, embeddingModel);
    }

    private ContentRetriever createEmptyRetriever(InMemoryEmbeddingStore<TextSegment> store, AllMiniLmL6V2EmbeddingModel model) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(model)
                .maxResults(3)
                .minScore(0.6)
                .build();
    }
}