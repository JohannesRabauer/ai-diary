package dev.rabauer.ai.diary.storage.rag;

import dev.langchain4j.model.chat.ChatModel;
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

    public RagAssistant createAssistant() {
        return AiServices
                .builder(RagAssistant.class)
                .chatModel(chatModel)
                .contentRetriever(
                        EmbeddingStoreContentRetriever.from(embeddingStore)
                )
                .build();
    }
}
