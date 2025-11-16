package dev.rabauer.ai.diary.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JpaChatMemoryProvider implements ChatMemoryProvider {

    private final  JpaChatMemoryStore store;

    public JpaChatMemoryProvider(JpaChatMemoryStore store) {
        this.store = store;
    }

    @Override
    public ChatMemory get(Object id) {
        return MessageWindowChatMemory.builder()
                .id(id.toString())
                .maxMessages(50)
                .chatMemoryStore(store)
                .build();
    }
}
