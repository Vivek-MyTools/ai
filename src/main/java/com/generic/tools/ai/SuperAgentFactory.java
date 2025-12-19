package com.generic.tools.ai;

import com.generic.tools.ai.agent.Assistant;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
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
    
    private static final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    public SuperAgentFactory(String baseUrl, String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    public Assistant createSuperAgent(String docFolderPath, Object... tools) {
        System.out.println("--- 🦸 Initializing Super Agent (" + this.modelName + ") ---");

        String cacheKey = this.baseUrl + "::" + this.modelName;
        
        ChatLanguageModel model = modelCache.computeIfAbsent(cacheKey, key -> {
            System.out.println("⚡ Building NEW Model instance for: " + this.modelName);
            return OllamaChatModel.builder()
                    .baseUrl(this.baseUrl)
                    .modelName(this.modelName)
                    .temperature(0.0)
                    .timeout(Duration.ofSeconds(60))
                    .build();
        });

        // Pass the user-selected folder path
        ContentRetriever retriever = setupRAG(docFolderPath);

        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tools)
                .contentRetriever(retriever)
                .build();
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

            // 2. MANUALLY collect files (Since this works in your logs!)
            try (Stream<Path> stream = Files.walk(docDir)) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    // Check if file matches our pattern
                if (matcher.matches(file)) {
                    try {
                        System.out.println("   --> Loading: " + file.getFileName());

                        dev.langchain4j.data.document.Document doc;
                        String fileName = file.getFileName().toString().toLowerCase();

                        // 1. EXPLICITLY choose the parser based on extension
                        if (fileName.endsWith(".txt") || fileName.endsWith(".java") || fileName.endsWith(".md")) {
                            doc = FileSystemDocumentLoader.loadDocument(file, new TextDocumentParser());
                        } 
                        else if (fileName.endsWith(".pdf")) {
                            // Requires 'langchain4j-document-parser-apache-pdfbox' dependency
                            doc = FileSystemDocumentLoader.loadDocument(file, new ApachePdfBoxDocumentParser());
                        } 
                        else {
                            // Fallback: Try to let the library guess
                            doc = FileSystemDocumentLoader.loadDocument(file);
                        }

                        allDocuments.add(doc);

                    } catch (Exception e) {
                        // 2. BETTER ERROR LOGGING
                        System.err.println("   ❌ Failed to read file: " + file.getFileName());
                        System.err.println("      Reason: " + e.getClass().getSimpleName() + " - " + e.getMessage());

                        // OneDrive Check
                        if (e.getMessage() != null && e.getMessage().contains("Access is denied")) {
                            System.err.println("      💡 HINT: This might be a OneDrive 'Cloud-only' file. Right-click the file and select 'Always keep on this device'.");
                        }
                    }
                }
                });
            } catch (IOException e) {
                System.err.println("❌ Error walking directory: " + e.getMessage());
            }

            // 3. Ingest whatever we found
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

// Keep helper
    private ContentRetriever createEmptyRetriever(InMemoryEmbeddingStore<TextSegment> store, AllMiniLmL6V2EmbeddingModel model) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(model)
                .maxResults(3)
                .minScore(0.6)
                .build();

    }
}
