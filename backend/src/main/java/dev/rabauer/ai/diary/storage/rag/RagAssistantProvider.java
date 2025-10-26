package dev.rabauer.ai.diary.storage.rag;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.rabauer.ai.diary.RagAssistant;
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RagAssistantProvider {

    @Inject
    PgVectorEmbeddingStore embeddingStore;

    @Inject
    ChatModel chatModel;
    @Inject
    EmbeddingModel embeddingModel;

    public RagAssistant createAssistant() {
        var contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .maxResults(3)
                .build();

        return AiServices
                .builder(RagAssistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();
    }
}
