package dev.rabauer.ai.diary.memory;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_history")
public class ChatMessageEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String sessionId;
    @Column(columnDefinition = "text")
    private String serializedMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSerializedMessage() {
        return serializedMessage;
    }

    public void setSerializedMessage(String additionalJson) {
        this.serializedMessage = additionalJson;
    }
}
