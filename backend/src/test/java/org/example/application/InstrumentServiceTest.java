package org.example.application;

import org.example.domain.analysis.BondAnalyzer;
import org.example.domain.analysis.DividendYieldCalculator;
import org.example.domain.analysis.ReturnsCalculator;
import org.example.domain.model.AmortizationPayment;
import org.example.domain.model.Bond;
import org.example.domain.model.CouponPayment;
import org.example.domain.model.DividendPayment;
import org.example.domain.model.Share;
import org.example.domain.port.BondHistoryProvider;
import org.example.domain.port.CorporateActionsProvider;
import org.example.infrastructure.moex.MoexClientException;
import org.example.infrastructure.persistence.BondRepository;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.infrastructure.persistence.ShareRepository;
import org.example.web.dto.InstrumentPaymentDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.example.web.dto.CandleDto;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock BondRepository bondRepository;
    @Mock ShareRepository shareRepository;
    @Mock InstrumentRepository instrumentRepository;
    @Mock BondAnalyzer bondAnalyzer;
    @Mock CorporateActionsProvider corporateActions;
    @Mock BondHistoryProvider bondHistory;
    @Mock CandleService candleService;
    @Mock BondDefaultService bondDefaultService;

    private InstrumentService service() {
        return new InstrumentService(bondRepository, shareRepository,
                instrumentRepository, bondAnalyzer, corporateActions, bondHistory,
                candleService, new ReturnsCalculator(), new DividendYieldCalculator(),
                bondDefaultService);
    }

    @Test
    void bondPaymentsMergeCouponsAndAmortizationsNewestFirst() {
        Bond bond = new Bond();
        bond.setSecId("SU26240");
        when(instrumentRepository.findBySecId("SU26240")).thenReturn(Optional.of(bond));
        when(corporateActions.coupons("SU26240")).thenReturn(List.of(
                new CouponPayment(LocalDate.of(2024, 1, 15), 34.9),
                new CouponPayment(LocalDate.of(2024, 7, 15), 34.9)));
        when(corporateActions.amortizations("SU26240")).thenReturn(List.of(
                new AmortizationPayment(LocalDate.of(2024, 7, 15), 1000.0)));

        List<InstrumentPaymentDto> out = service().payments("SU26240");

        assertThat(out).extracting(InstrumentPaymentDto::date)
                .isSortedAccordingTo((a, b) -> b.compareTo(a)); // новые сверху
        assertThat(out).extracting(InstrumentPaymentDto::kind)
                .containsExactlyInAnyOrder("COUPON", "COUPON", "AMORTIZATION");
        assertThat(out).allMatch(p -> p.currency().equals("RUB"));
    }

    @Test
    void sharePaymentsMapDividends() {
        Share share = new Share();
        share.setSecId("SBER");
        when(instrumentRepository.findBySecId("SBER")).thenReturn(Optional.of(share));
        when(corporateActions.dividends("SBER")).thenReturn(List.of(
                new DividendPayment(LocalDate.of(2024, 7, 11), 33.3, "RUB")));

        List<InstrumentPaymentDto> out = service().payments("SBER");

        assertThat(out).singleElement().satisfies(p -> {
            assertThat(p.kind()).isEqualTo("DIVIDEND");
            assertThat(p.perUnit()).isEqualTo(33.3);
            assertThat(p.currency()).isEqualTo("RUB");
        });
    }

    @Test
    void bondPaymentShareIsCountedFromMoneyPriceNotFromPercentQuote() {
        // Котировка облигации — 98% от номинала 1000 ₽, то есть 980 ₽ за бумагу.
        // Купон 34.9 ₽ — это 3.56% от цены; если поделить на саму котировку «98»,
        // получилось бы 35.6% — завышение в десять раз.
        Bond bond = new Bond();
        bond.setSecId("SU26240");
        bond.setFaceValue(1000.0);
        bond.setLastPrice(98.0);
        when(instrumentRepository.findBySecId("SU26240")).thenReturn(Optional.of(bond));
        when(corporateActions.coupons("SU26240")).thenReturn(List.of(
                new CouponPayment(LocalDate.of(2024, 1, 15), 34.9)));
        when(corporateActions.amortizations("SU26240")).thenReturn(List.of());

        assertThat(service().payments("SU26240")).singleElement().satisfies(p ->
                assertThat(p.sharePct()).isCloseTo(3.561, within(0.01)));
    }

    @Test
    void sharePaymentShareFallsBackToCurrentPriceWithoutCandles() {
        Share share = new Share();
        share.setSecId("SBER");
        share.setLastPrice(250.0);
        when(instrumentRepository.findBySecId("SBER")).thenReturn(Optional.of(share));
        when(corporateActions.dividends("SBER")).thenReturn(List.of(
                new DividendPayment(LocalDate.of(2024, 7, 11), 33.3, "RUB")));

        assertThat(service().payments("SBER")).singleElement().satisfies(p ->
                assertThat(p.sharePct()).isCloseTo(13.32, within(0.01)));
    }

    @Test
    void pastDividendUsesPriceOnItsOwnDateNotTodaysPrice() {
        // Акция стоила 100 ₽ на отсечке и стоит 250 ₽ сейчас. Дивиденд 10 ₽ был доходностью 10%,
        // и пересчёт по сегодняшней цене превратил бы его в 4% — число, которого не существовало.
        LocalDate recordDate = LocalDate.now().minusYears(1);
        Share share = new Share();
        share.setSecId("SBER");
        share.setLastPrice(250.0);
        when(instrumentRepository.findBySecId("SBER")).thenReturn(Optional.of(share));
        when(corporateActions.dividends("SBER")).thenReturn(List.of(
                new DividendPayment(recordDate, 10.0, "RUB")));
        when(candleService.getCandles(eq("SBER"), eq(24), any(), any())).thenReturn(List.of(
                candle(recordDate.minusDays(1), 100.0)));

        assertThat(service().payments("SBER")).singleElement().satisfies(p ->
                assertThat(p.sharePct()).isCloseTo(10.0, within(0.01)));
    }

    @Test
    void announcedFutureDividendUsesCurrentPrice() {
        // У будущей выплаты другой цены и быть не может
        LocalDate future = LocalDate.now().plusMonths(2);
        Share share = new Share();
        share.setSecId("SBER");
        share.setLastPrice(200.0);
        when(instrumentRepository.findBySecId("SBER")).thenReturn(Optional.of(share));
        when(corporateActions.dividends("SBER")).thenReturn(List.of(
                new DividendPayment(future, 20.0, "RUB")));

        assertThat(service().payments("SBER")).singleElement().satisfies(p ->
                assertThat(p.sharePct()).isCloseTo(10.0, within(0.01)));
    }

    @Test
    void bondPriceOnDateIsConvertedFromPercentOfFaceValue() {
        // Котировка облигации в свече — проценты номинала. Купон 34.9 ₽ при котировке 98
        // и номинале 1000 ₽ — это 3.56% от 980 ₽, а не 35.6% от «98»
        LocalDate couponDate = LocalDate.now().minusMonths(6);
        Bond bond = new Bond();
        bond.setSecId("SU26240");
        bond.setFaceValue(1000.0);
        bond.setLastPrice(80.0);           // сегодняшняя цена другая — не должна участвовать
        when(instrumentRepository.findBySecId("SU26240")).thenReturn(Optional.of(bond));
        when(corporateActions.coupons("SU26240")).thenReturn(List.of(
                new CouponPayment(couponDate, 34.9)));
        when(corporateActions.amortizations("SU26240")).thenReturn(List.of());
        when(candleService.getCandles(eq("SU26240"), eq(24), any(), any())).thenReturn(List.of(
                candle(couponDate, 98.0)));

        assertThat(service().payments("SU26240")).singleElement().satisfies(p ->
                assertThat(p.sharePct()).isCloseTo(3.561, within(0.01)));
    }

    private CandleDto candle(LocalDate date, double close) {
        long ts = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        return new CandleDto(ts, close, close, close, close, 0);
    }

    @Test
    void shareIsNullWhenPriceIsUnknown() {
        // Без цены доля не определена — показать 0% значило бы соврать
        Share share = new Share();
        share.setSecId("NOPRICE");
        when(instrumentRepository.findBySecId("NOPRICE")).thenReturn(Optional.of(share));
        when(corporateActions.dividends("NOPRICE")).thenReturn(List.of(
                new DividendPayment(LocalDate.of(2024, 7, 11), 10.0, "RUB")));

        assertThat(service().payments("NOPRICE")).singleElement().satisfies(p ->
                assertThat(p.sharePct()).isNull());
    }

    @Test
    void degradesToEmptyWhenMoexUnavailable() {
        Bond bond = new Bond();
        bond.setSecId("RU000A");
        when(instrumentRepository.findBySecId("RU000A")).thenReturn(Optional.of(bond));
        when(corporateActions.coupons("RU000A"))
                .thenThrow(new MoexClientException("HTTP 404", null));

        assertThat(service().payments("RU000A")).isEmpty();
    }

    @Test
    void throwsNotFoundForUnknownSecId() {
        when(instrumentRepository.findBySecId("NOPE")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().payments("NOPE"))
                .isInstanceOf(NotFoundException.class);
    }
}
