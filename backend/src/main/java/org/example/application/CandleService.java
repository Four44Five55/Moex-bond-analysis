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
import java.util.ArrayList;
import java.util.List;

/**
 * Свечи по инструменту по принципу cache-aside: сначала БД, недостающие отрезки периода
 * дозагружаются из MOEX и сохраняются. Хранилище догружается инкрементально — накопленная
 * история не перезапрашивается целиком, но и не устаревает. Поверх — Redis-кеш идентичных запросов.
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
        Instant fromTs = startOf(from);
        Instant toTs = endOf(to);

        List<Candle> stored = candleRepository
                .findBySecIdAndIntervalCodeAndTsBetweenOrderByTs(secId, intervalCode, fromTs, toTs);

        List<DateRange> gaps = missingRanges(stored, from, to);
        if (!gaps.isEmpty() && loadRanges(secId, intervalCode, gaps)) {
            stored = candleRepository
                    .findBySecIdAndIntervalCodeAndTsBetweenOrderByTs(secId, intervalCode, fromTs, toTs);
        }
        return toDtos(stored);
    }

    /**
     * Отрезки запрошенного периода, которых нет в БД: «голова» до самой ранней сохранённой свечи
     * и «хвост» после самой поздней. Хвост берётся <b>включая</b> последнюю сохранённую дату —
     * свеча текущего дня могла быть записана незакрытой и должна перезаписаться.
     */
    private List<DateRange> missingRanges(List<Candle> stored, LocalDate from, LocalDate to) {
        if (stored.isEmpty()) {
            return List.of(new DateRange(from, to));
        }
        LocalDate earliest = dateOf(stored.get(0).getTs());
        LocalDate latest = dateOf(stored.get(stored.size() - 1).getTs());

        List<DateRange> gaps = new ArrayList<>(2);
        if (earliest.isAfter(from)) {
            gaps.add(new DateRange(from, earliest));
        }
        if (latest.isBefore(to)) {
            gaps.add(new DateRange(latest, to));
        }
        return gaps;
    }

    /** @return true, если хотя бы один отрезок принёс свечи (значит, выборку надо перечитать). */
    private boolean loadRanges(String secId, int intervalCode, List<DateRange> ranges) {
        Instrument instrument = instrumentRepository.findBySecId(secId)
                .orElseThrow(() -> new NotFoundException("Инструмент не найден: " + secId));

        boolean loaded = false;
        for (DateRange range : ranges) {
            List<Candle> fetched = candleProvider.fetchCandles(
                    secId, instrument.getMarket(), intervalCode, range.from(), range.to());
            if (fetched.isEmpty()) {
                continue;
            }
            // Перезапись отрезка: удаление обязано уйти в БД до вставок, иначе Hibernate
            // выполнит INSERT раньше DELETE и нарушит uq_candle (sec_id, interval_code, ts).
            candleRepository.deleteBySecIdAndIntervalCodeAndTsBetween(
                    secId, intervalCode, startOf(range.from()), endOf(range.to()));
            candleRepository.flush();
            candleRepository.saveAll(fetched);
            logger.debug("Загружено {} свечей для {} за {}..{}",
                    fetched.size(), secId, range.from(), range.to());
            loaded = true;
        }
        return loaded;
    }

    /** Отрезок дат, подлежащий загрузке из MOEX (границы включительно). */
    private record DateRange(LocalDate from, LocalDate to) {
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant endOf(LocalDate date) {
        return date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }

    private LocalDate dateOf(Instant ts) {
        return ts.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private List<CandleDto> toDtos(List<Candle> candles) {
        return candles.stream()
                .map(c -> new CandleDto(
                        c.getTs().getEpochSecond(),
                        c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), c.getVolume()))
                .toList();
    }
}
