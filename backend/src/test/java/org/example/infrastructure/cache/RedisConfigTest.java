package org.example.infrastructure.cache;

import org.example.domain.model.CouponPayment;
import org.example.domain.model.InstrumentType;
import org.example.web.dto.CandleDto;
import org.example.web.dto.DividendInfoDto;
import org.example.web.dto.InstrumentDto;
import org.example.web.dto.InstrumentPaymentDto;
import org.example.web.dto.PagedResponse;
import org.example.web.dto.PerformanceDto;
import org.example.web.dto.SummaryDto;
import org.example.web.dto.portfolio.PortfolioHistoryDto;
import org.example.web.dto.portfolio.ValuePointDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Валидатор типов в {@link RedisConfig} сужен до пакетов приложения и стандартных типов.
 * Сузить слишком сильно — значит уронить чтение кеша уже в проде, поэтому здесь через тот же
 * сериализатор прогоняется по представителю каждого кешируемого значения.
 */
class RedisConfigTest {

    @SuppressWarnings("unchecked")
    private final SerializationPair<Object> pair =
            (SerializationPair<Object>) new RedisConfig().cacheConfiguration().getValueSerializationPair();

    private Object roundTrip(Object value) {
        ByteBuffer written = pair.write(value);
        return pair.read(written);
    }

    @Test
    void restoresPaymentsList() {
        List<InstrumentPaymentDto> payments = List.of(
                new InstrumentPaymentDto(LocalDate.of(2025, 7, 15), "COUPON", 34.9, "RUB"),
                new InstrumentPaymentDto(LocalDate.of(2025, 1, 15), "AMORTIZATION", 1000.0, "RUB"));

        assertThat(roundTrip(payments)).isEqualTo(payments);
    }

    @Test
    void restoresCorporateActionsFromDomain() {
        List<CouponPayment> coupons = List.of(new CouponPayment(LocalDate.of(2026, 2, 1), 41.5));

        assertThat(roundTrip(coupons)).isEqualTo(coupons);
    }

    @Test
    void restoresPerformanceWithNullableFields() {
        PerformanceDto performance = new PerformanceDto(287.4, 1.2, null, -3.4, null, 8.9, 310.0, 240.5);

        assertThat(roundTrip(performance)).isEqualTo(performance);
    }

    @Test
    void restoresDividendInfoAndCandles() {
        assertThat(roundTrip(new DividendInfoDto(33.3, 11.6, 2)))
                .isEqualTo(new DividendInfoDto(33.3, 11.6, 2));
        assertThat(roundTrip(new DividendInfoDto(null, null, 0)))
                .isEqualTo(new DividendInfoDto(null, null, 0));

        List<CandleDto> candles = List.of(new CandleDto(1_753_400_000L, 100.1, 102.0, 99.5, 101.2, 5000));
        assertThat(roundTrip(candles)).isEqualTo(candles);
    }

    @Test
    void restoresPagedInstrumentsWithEnumAndSummary() {
        InstrumentDto bond = new InstrumentDto(
                "SU26240", InstrumentType.BOND, "ОФЗ 26240", "ОФЗ-ПД 26240", "RU000A103BR0",
                "SUR", "TQOB", "bonds", 78.9, 79.1, -0.25, 1_000_000.0,
                1000.0, 7.0, 13.4, 8.2, null, 789.0, 70.0, 2,
                LocalDate.of(2036, 7, 30), LocalDate.of(2026, 1, 14), LocalDate.of(2021, 5, 26),
                null, 8, true, false, false, false, 12.3, 8.87, null,
                null, null, null, 1);

        PagedResponse<InstrumentDto> page = new PagedResponse<>(
                List.of(bond), 0, 50, 1, 1, new SummaryDto(1, 13.4, 8.0, 1.0, 13.4));

        assertThat(roundTrip(page)).isEqualTo(page);
    }

    @Test
    void restoresPortfolioHistory() {
        PortfolioHistoryDto history = new PortfolioHistoryDto(
                List.of(new ValuePointDto(LocalDate.of(2025, 7, 25), 125_000.0, 100_000.0, 25_000.0)),
                14.72);

        assertThat(roundTrip(history)).isEqualTo(history);
    }
}
