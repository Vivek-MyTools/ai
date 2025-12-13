package com.generic.tools.ai;

import com.generic.tools.ai.agent.Assistant;
import com.generic.tools.ai.tools.WeatherTool; 

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
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

import java.nio.file.*;
import java.time.Duration;

public class SuperAgentFactory {

    private final String baseUrl;
    private final String modelName;
    
    // Define the persistent file path
    private static final String EMBEDDING_FILE = "src/main/resources/embeddings.json";

    // 1. Default Constructor (Standard Settings)
    public SuperAgentFactory() {
        this.baseUrl = "http://localhost:11434";
        this.modelName = "gpt-oss:20b-cloud";
    }

    // 2. Custom Constructor (For A/B Testing or different models)
    public SuperAgentFactory(String baseUrl, String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    public Assistant createSuperAgent(String docFolderPath) {
        System.out.println("--- 🦸 Initializing Super Agent (" + this.modelName + ") ---");

        // 3. Configure Model (Uses instance variables)
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(this.baseUrl)
                .modelName(this.modelName)
                .temperature(0.0) // 0.0 is best for mixing Tools + RAG
                .timeout(Duration.ofSeconds(60))
                .build();

        // 4. Setup RAG (Uses helper method)
        ContentRetriever retriever = setupRAG(docFolderPath);

        // 5. Build the Agent
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(new WeatherTool())   // <--- Capability A: Tools
                .contentRetriever(retriever) // <--- Capability B: Docs
                .build();
    }

    // Helper Method to handle the "Brain" logic
    private ContentRetriever setupRAG(String folderPath) {
        AllMiniLmL6V2EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        InMemoryEmbeddingStore<TextSegment> embeddingStore;
        Path storePath = Paths.get(EMBEDDING_FILE);

        // Persistence Logic: Load if exists, Scan if new
        if (Files.exists(storePath)) {
            System.out.println("💾 Loading existing brain from file...");
            embeddingStore = InMemoryEmbeddingStore.fromFile(storePath);
        } else {
            System.out.println("📂 Scanning documents in: " + folderPath);
            embeddingStore = new InMemoryEmbeddingStore<>();
            
            // Filter for safe files
            PathMatcher filter = FileSystems.getDefault().getPathMatcher("glob:*.{pdf,docx,txt,TXT,java}");
            var documents = FileSystemDocumentLoader.loadDocuments(Paths.get(folderPath), filter);

            if (!documents.isEmpty()) {
                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .documentSplitter(DocumentSplitters.recursive(300, 20))
                        .build();
                ingestor.ingest(documents);
                
                // Save to disk
                embeddingStore.serializeToFile(storePath);
                System.out.println("✅ Indexed & Saved " + documents.size() + " files.");
            } else {
                System.out.println("⚠️ No documents found to index.");
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