package org.example.application;

import org.example.application.event.DataRefreshedEvent;
import org.example.domain.model.Bond;
import org.example.domain.model.Instrument;
import org.example.domain.model.Share;
import org.example.domain.port.MarketDataProvider;
import org.example.infrastructure.moex.MoexClientException;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Синхронизация данных MOEX → PostgreSQL. Облигации и акции загружаются <b>параллельно</b>
 * (CompletableFuture на выделенном пуле), затем полиморфный апсерт по sec_id. По завершении
 * сбрасываются кеши и публикуется {@link DataRefreshedEvent} (Observer).
 */
@Service
public class SyncService {
    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);

    private final MarketDataProvider marketDataProvider;
    private final InstrumentRepository instrumentRepository;
    private final Executor moexExecutor;
    private final ApplicationEventPublisher eventPublisher;

    public SyncService(MarketDataProvider marketDataProvider,
                       InstrumentRepository instrumentRepository,
                       @Qualifier("moexExecutor") Executor moexExecutor,
                       ApplicationEventPublisher eventPublisher) {
        this.marketDataProvider = marketDataProvider;
        this.instrumentRepository = instrumentRepository;
        this.moexExecutor = moexExecutor;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    // Свежие котировки меняют и производные от них ответы — иначе карточка инструмента
    // обновляется, а её доходности показывают старую цену, пока не истечёт TTL
    @Caching(evict = {
            @CacheEvict(cacheNames = "instruments", allEntries = true),
            @CacheEvict(cacheNames = "instrument", allEntries = true),
            @CacheEvict(cacheNames = "summary", allEntries = true),
            @CacheEvict(cacheNames = "performance", allEntries = true),
            @CacheEvict(cacheNames = "dividendYield", allEntries = true)
    })
    public int syncAll() {
        long start = System.currentTimeMillis();

        CompletableFuture<List<Bond>> bondsFuture =
                CompletableFuture.supplyAsync(marketDataProvider::fetchBonds, moexExecutor);
        CompletableFuture<List<Share>> sharesFuture =
                CompletableFuture.supplyAsync(marketDataProvider::fetchShares, moexExecutor);

        List<Instrument> fetched = new ArrayList<>();
        fetched.addAll(await(bondsFuture));
        fetched.addAll(await(sharesFuture));

        int count = upsert(fetched);
        long elapsed = System.currentTimeMillis() - start;
        logger.info("Синхронизация завершена: {} инструментов за {} мс (параллельно)", count, elapsed);

        eventPublisher.publishEvent(new DataRefreshedEvent(count));
        return count;
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof MoexClientException moex) {
                throw moex;
            }
            throw e;
        }
    }

    private int upsert(List<Instrument> fetched) {
        Map<String, Long> existingIds = instrumentRepository.findAll().stream()
                .collect(Collectors.toMap(Instrument::getSecId, Instrument::getId, (a, b) -> a));

        for (Instrument instrument : fetched) {
            Long existingId = existingIds.get(instrument.getSecId());
            if (existingId != null) {
                instrument.setId(existingId);
            }
        }

        instrumentRepository.saveAll(fetched);
        return fetched.size();
    }
}
