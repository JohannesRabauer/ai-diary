package dev.rabauer.ai.diary;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.rabauer.ai.diary.memory.ChatMessageRepository;
import dev.rabauer.ai.diary.memory.JpaChatMemoryProvider;
import dev.rabauer.ai.diary.memory.JpaChatMemoryStore;
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DiaryAiAssistantProvider {

    /**
     * This could be variable if multiple users are using the system
     */
    public static final int SESSION_ID = 1;

    private final PgVectorEmbeddingStore embeddingStore;
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final JpaChatMemoryProvider memoryProvider;
    private final ChatMessageRepository repository;

    public DiaryAiAssistantProvider(
            PgVectorEmbeddingStore embeddingStore,
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            JpaChatMemoryProvider memoryProvider,
            ChatMessageRepository repository
    ) {
        this.embeddingStore = embeddingStore;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.memoryProvider = memoryProvider;
        this.repository = repository;
    }

    public DiaryAiAssistant createAssistant() {
        var contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .maxResults(3)
                .build();

        return AiServices
                .builder(DiaryAiAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .contentRetriever(contentRetriever)
                .build();
    }
}
