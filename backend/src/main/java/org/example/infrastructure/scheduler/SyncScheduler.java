package org.example.infrastructure.scheduler;

import org.example.application.SyncService;
import org.example.infrastructure.moex.MoexProperties;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Периодическая (cron) и стартовая синхронизация с MOEX.
 * На старте загружает данные, если БД пуста; далее — по расписанию moex.sync.cron.
 */
@Component
public class SyncScheduler implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncService syncService;
    private final InstrumentRepository instrumentRepository;
    private final MoexProperties properties;

    public SyncScheduler(SyncService syncService,
                         InstrumentRepository instrumentRepository,
                         MoexProperties properties) {
        this.syncService = syncService;
        this.instrumentRepository = instrumentRepository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.sync().enabled() && instrumentRepository.count() == 0) {
            logger.info("БД пуста — выполняю стартовую синхронизацию с MOEX");
            safeSync();
        }
    }

    @Scheduled(cron = "${moex.sync.cron}")
    public void scheduledSync() {
        if (properties.sync().enabled()) {
            logger.info("Плановая синхронизация с MOEX");
            safeSync();
        }
    }

    private void safeSync() {
        try {
            syncService.syncAll();
        } catch (Exception e) {
            logger.error("Синхронизация с MOEX не удалась: {}", e.getMessage());
        }
    }
}
