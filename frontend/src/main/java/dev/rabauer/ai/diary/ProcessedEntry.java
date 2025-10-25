package dev.rabauer.ai.diary;

import java.time.LocalDateTime;

public record ProcessedEntry(
        LocalDateTime entryTimestamp,
        String entryText,
        String entryAiText
) {
}
