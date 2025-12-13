package com.generic.tools.ai;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.Duration;

import com.generic.tools.ai.agent.Assistant;

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class RagAgentFactory {

    private final String baseUrl;
    private final String modelName;
    
    // Define where to save the "Brain" on disk
    private static final String EMBEDDING_FILE = "src/main/resources/embeddings.json";

    // Default Constructor
    public RagAgentFactory() {
        this.baseUrl = "http://localhost:11434";
        this.modelName = "gpt-oss:20b-cloud";
    }

    // Configurable Constructor
    public RagAgentFactory(String baseUrl, String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    // Instance Method
    public Assistant createAssistant(String folderPath) {
        System.out.println("--- 🚀 Initializing RAG Agent (" + modelName + ") ---");

        // 1. Validation
        Path path = Paths.get(folderPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new RuntimeException("The folder '" + folderPath + "' does not exist!");
        }

        // 2. Configure Model
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(this.baseUrl)
                .modelName(this.modelName)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(60))
                .build();

        // 3. Configure Embedding Model (The "Translator")
        AllMiniLmL6V2EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        // 4. Setup Embedding Store with Persistence Logic
        InMemoryEmbeddingStore<TextSegment> embeddingStore;
        Path storePath = Paths.get(EMBEDDING_FILE);

        if (Files.exists(storePath)) {
            // --- OPTION A: FAST LOAD ---
            System.out.println("💾 Found existing embedding file. Loading from disk...");
            embeddingStore = InMemoryEmbeddingStore.fromFile(storePath);
            System.out.println("⚡ Brain loaded instantly! (Skipping document scan)");
        } else {
            // --- OPTION B: FIRST RUN (SCAN & SAVE) ---
            System.out.println("📂 No saved brain found. Scanning documents in: " + folderPath);
            embeddingStore = new InMemoryEmbeddingStore<>();

            // Your Filter Logic
            PathMatcher filter = FileSystems.getDefault().getPathMatcher("glob:*.{pdf,docx,txt,TXT}");
            var documents = FileSystemDocumentLoader.loadDocuments(path, filter);

            if (documents.isEmpty()) {
                System.out.println("⚠️ No documents found. Agent will have no external knowledge.");
            } else {
                // Split and Ingest
                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .documentSplitter(DocumentSplitters.recursive(300, 20))
                        .build();
                ingestor.ingest(documents);
                System.out.println("✅ Indexed " + documents.size() + " files.");

                // SAVE to Disk
                System.out.println("💾 Saving embeddings to file for next time...");
                embeddingStore.serializeToFile(storePath);
                System.out.println("✅ Saved to: " + storePath.toAbsolutePath());
            }
        }

        // 5. Retriever
        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();

        // 6. Build Agent
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(retriever)
                .build();
    }
}