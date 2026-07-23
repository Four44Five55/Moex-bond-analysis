package org.example.domain.port;

import org.example.domain.model.Candle;

import java.time.LocalDate;
import java.util.List;

/** Порт получения исторических свечей инструмента из внешнего источника. */
public interface CandleProvider {

    List<Candle> fetchCandles(String secId, String market, int intervalCode, LocalDate from, LocalDate to);
}
