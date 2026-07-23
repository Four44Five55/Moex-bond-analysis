package org.example.application.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Реакция на завершение синхронизации (Observer). Кеши уже сброшены в SyncService;
 * здесь — точка расширения для прогрева кешей/уведомлений.
 */
@Component
public class DataRefreshedListener {
    private static final Logger logger = LoggerFactory.getLogger(DataRefreshedListener.class);

    @EventListener
    public void onDataRefreshed(DataRefreshedEvent event) {
        logger.info("Данные обновлены: {} инструментов в {}", event.instrumentsSynced(), event.at());
    }
}
