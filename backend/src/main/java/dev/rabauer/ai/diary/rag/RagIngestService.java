package dev.rabauer.ai.diary.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.embedding.*;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

@ApplicationScoped
public class RagIngestService {

    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;
    private final DiaryEntryRepository repository;

    public RagIngestService(
            EmbeddingModel embeddingModel,
            PgVectorEmbeddingStore embeddingStore,
            DiaryEntryRepository repository
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.repository = repository;
    }

    @Transactional
    public void embedAndStoreNewEntry(DiaryEntryEntity newEntity)
    {
        repository.persist(newEntity);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(recursive(500, 0))
                .build();

        ingestor.ingest(
                Document.from(newEntity.entryText,
                        Metadata.from(
                                Map.of(
                                        "Timestamp",DateTimeFormatter.ISO_DATE_TIME.format(newEntity.timestamp) ,
                                        "AI hint", newEntity.aiContent
                                )
                        ))
        );
    }
}
