package dev.rabauer.ai.diary.dto;

import java.time.LocalDateTime;

public record NewEntry(
        String entryText,
        LocalDateTime entryTimestamp
) {
}
