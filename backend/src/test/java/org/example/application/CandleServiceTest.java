package org.example.application;

import org.example.domain.model.Candle;
import org.example.domain.model.Share;
import org.example.domain.port.CandleProvider;
import org.example.infrastructure.persistence.CandleRepository;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Правила дозагрузки свечей: что именно считается «недостающим отрезком».
 * Ошибка здесь не падает и не видна в UI — просто график тихо замирает на старых данных.
 */
class CandleServiceTest {

    private static final int DAILY = 24;
    private static final String SEC_ID = "SBER";

    private CandleRepository candleRepository;
    private CandleProvider candleProvider;
    private CandleService service;

    @BeforeEach
    void setUp() {
        candleRepository = mock(CandleRepository.class);
        candleProvider = mock(CandleProvider.class);
        InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);

        Share share = new Share();
        share.setSecId(SEC_ID);
        share.setMarket("shares");
        when(instrumentRepository.findBySecId(SEC_ID)).thenReturn(Optional.of(share));

        service = new CandleService(candleRepository, candleProvider, instrumentRepository);
    }

    @Test
    void loadsWholePeriodWhenNothingStored() {
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now();
        storedCandles(List.of());

        service.getCandles(SEC_ID, DAILY, from, to);

        verify(candleProvider).fetchCandles(SEC_ID, "shares", DAILY, from, to);
    }

    @Test
    void refetchesTodayBecauseItsCandleIsUnclosed() {
        // История уже дотягивается до конца периода, но последняя свеча — за сегодня:
        // она записана незакрытой, и её цена закрытия обязана обновиться.
        LocalDate today = LocalDate.now();
        storedCandles(List.of(candle(today.minusDays(1)), candle(today)));

        service.getCandles(SEC_ID, DAILY, today.minusDays(5), today);

        verify(candleProvider).fetchCandles(SEC_ID, "shares", DAILY, today, today);
    }

    @Test
    void doesNotRefetchClosedPeriodInThePast() {
        // Период целиком в прошлом и покрыт историей — ходить в MOEX незачем.
        LocalDate to = LocalDate.now().minusDays(30);
        LocalDate from = to.minusDays(5);
        storedCandles(List.of(candle(from), candle(to)));

        service.getCandles(SEC_ID, DAILY, from, to);

        verify(candleProvider, never()).fetchCandles(anyString(), anyString(), anyInt(), any(), any());
    }

    @Test
    void loadsMissingHeadBeforeEarliestStoredCandle() {
        LocalDate to = LocalDate.now().minusDays(30);
        LocalDate from = to.minusDays(10);
        LocalDate earliest = to.minusDays(4);
        storedCandles(List.of(candle(earliest), candle(to)));

        service.getCandles(SEC_ID, DAILY, from, to);

        verify(candleProvider).fetchCandles(SEC_ID, "shares", DAILY, from, earliest);
    }

    private void storedCandles(List<Candle> candles) {
        when(candleRepository.findBySecIdAndIntervalCodeAndTsBetweenOrderByTs(
                eq(SEC_ID), eq(DAILY), any(), any())).thenReturn(candles);
    }

    private Candle candle(LocalDate date) {
        Candle c = new Candle();
        c.setSecId(SEC_ID);
        c.setMarket("shares");
        c.setIntervalCode(DAILY);
        c.setTs(date.atStartOfDay(ZoneOffset.UTC).toInstant());
        c.setClose(100.0);
        return c;
    }
}
