package org.example.application;

import org.example.domain.model.Candle;
import org.example.domain.model.Instrument;
import org.example.domain.port.CandleProvider;
import org.example.infrastructure.persistence.CandleRepository;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.web.dto.CandleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Свечи по инструменту по принципу cache-aside: сначала БД, при отсутствии — загрузка
 * из MOEX с сохранением. Поверх — Redis-кеш идентичных запросов.
 */
@Service
public class CandleService {
    private static final Logger logger = LoggerFactory.getLogger(CandleService.class);

    private final CandleRepository candleRepository;
    private final CandleProvider candleProvider;
    private final InstrumentRepository instrumentRepository;

    public CandleService(CandleRepository candleRepository,
                         CandleProvider candleProvider,
                         InstrumentRepository instrumentRepository) {
        this.candleRepository = candleRepository;
        this.candleProvider = candleProvider;
        this.instrumentRepository = instrumentRepository;
    }

    @Cacheable(cacheNames = "candles",
            key = "#secId + ':' + #intervalCode + ':' + #from + ':' + #to")
    @Transactional
    public List<CandleDto> getCandles(String secId, int intervalCode, LocalDate from, LocalDate to) {
        Instant fromTs = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTs = to.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);

        List<Candle> stored = candleRepository
                .findBySecIdAndIntervalCodeAndTsBetweenOrderByTs(secId, intervalCode, fromTs, toTs);
        if (!stored.isEmpty()) {
            return toDtos(stored);
        }

        Instrument instrument = instrumentRepository.findBySecId(secId)
                .orElseThrow(() -> new NotFoundException("Инструмент не найден: " + secId));

        List<Candle> fetched = candleProvider.fetchCandles(
                secId, instrument.getMarket(), intervalCode, from, to);
        if (!fetched.isEmpty()) {
            candleRepository.deleteBySecIdAndIntervalCodeAndTsBetween(secId, intervalCode, fromTs, toTs);
            candleRepository.saveAll(fetched);
            logger.debug("Сохранено {} свечей для {}", fetched.size(), secId);
        }
        return toDtos(fetched);
    }

    private List<CandleDto> toDtos(List<Candle> candles) {
        return candles.stream()
                .map(c -> new CandleDto(
                        c.getTs().getEpochSecond(),
                        c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), c.getVolume()))
                .toList();
    }
}
