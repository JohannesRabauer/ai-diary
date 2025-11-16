package dev.rabauer.ai.diary.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class JpaChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository repository;

    public JpaChatMemoryStore(ChatMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        return repository.findBySession(sessionId)
                   .stream()
                   .map(this::toChatMessage)
                   .toList();
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = memoryId.toString();
        // Delete existing for that session
        repository.delete("sessionId", sessionId);
        for (ChatMessage msg : messages) {
            persistMessage(sessionId, msg);
        }
    }

    private void persistMessage(String sessionId, ChatMessage msg) {
        ChatMessageEntity e = new ChatMessageEntity();
        e.setSessionId(sessionId);
        String json = ChatMessageSerializer.messageToJson(msg);
        e.setSerializedMessage(json);
        repository.persist(e);
    }

    private ChatMessage toChatMessage(ChatMessageEntity entity) {
        String json = entity.getSerializedMessage();
        return ChatMessageDeserializer.messageFromJson(json);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        repository.delete("sessionId", sessionId);
    }
}
