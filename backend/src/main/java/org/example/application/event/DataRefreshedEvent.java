package org.example.application.event;

import java.time.Instant;

/** Событие завершения синхронизации данных MOEX (Observer). */
public record DataRefreshedEvent(int instrumentsSynced, Instant at) {
    public DataRefreshedEvent(int instrumentsSynced) {
        this(instrumentsSynced, Instant.now());
    }
}
