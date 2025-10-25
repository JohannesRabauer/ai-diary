package dev.rabauer.ai.diary;

import java.time.LocalDateTime;

public record NewEntry(
        String entryText,
        LocalDateTime entryTimestamp
) {
}
