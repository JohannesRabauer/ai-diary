package dev.rabauer.ai.diary.storage.entities;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DiaryEntryRepository implements PanacheRepository<DiaryEntryEntity> {
}
