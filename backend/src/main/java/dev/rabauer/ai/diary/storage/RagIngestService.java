package dev.rabauer.ai.diary.storage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.*;
import dev.rabauer.ai.diary.storage.entities.DiaryEntryEntity;
import dev.rabauer.ai.diary.storage.entities.DiaryEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.format.DateTimeFormatter;

import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;

@ApplicationScoped
public class RagIngestService {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    PgVectorEmbeddingStore embeddingStore;

    @Inject
    DiaryEntryRepository repository;

    @Transactional
    public void embedAndStoreNewEntry(DiaryEntryEntity newEntity) {

        String formattedTimestamp = DateTimeFormatter.ISO_DATE_TIME.format(newEntity.timestamp);

        String combined = "Timestamp: " + formattedTimestamp + "\n"
                + "AI hint: " + newEntity.aiContent + "\n"
                + "Diary: " + newEntity.entryText;

        Embedding embedding = embeddingModel.embed(combined).content();

        newEntity.embedding = embedding.vector();
        repository.persist(newEntity);

        embeddingStore.add(embedding);
    }
}
