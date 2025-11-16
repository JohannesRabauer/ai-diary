package dev.rabauer.ai.diary.rag;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class DiaryEntryEntity extends PanacheEntity {

    @Column(columnDefinition = "TEXT")
    public String entryText;

    public LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    public String aiContent;

    // Store embedding as a float[] in memory, mapped to pgvector in DB
    @Column(columnDefinition = "vector(1024)")
    public float[] embedding;

    public DiaryEntryEntity(String entryText, LocalDateTime timestamp, String aiContent) {
        this.entryText = entryText;
        this.timestamp = timestamp;
        this.aiContent = aiContent;
    }

    public DiaryEntryEntity() {
    }
}
