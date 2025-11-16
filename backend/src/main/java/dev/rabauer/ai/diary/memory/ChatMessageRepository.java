package dev.rabauer.ai.diary.memory;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ChatMessageRepository implements PanacheRepository<ChatMessageEntity> {

    public List<ChatMessageEntity> findBySession(String sessionId) {
        return find("sessionId", sessionId).list();
    }
}
