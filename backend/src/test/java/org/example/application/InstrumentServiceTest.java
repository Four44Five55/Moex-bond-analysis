package org.example.application;

import org.example.domain.analysis.BondAnalyzer;
import org.example.domain.analysis.DividendYieldCalculator;
import org.example.domain.analysis.ReturnsCalculator;
import org.example.domain.model.AmortizationPayment;
import org.example.domain.model.Bond;
import org.example.domain.model.CouponPayment;
import org.example.domain.model.DividendPayment;
import org.example.domain.model.Share;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock BondRepository bondRepository;
    @Mock ShareRepository shareRepository;
    @Mock InstrumentRepository instrumentRepository;
    @Mock BondAnalyzer bondAnalyzer;
    @Mock CorporateActionsProvider corporateActions;
    @Mock CandleService candleService;

    private InstrumentService service() {
        return new InstrumentService(bondRepository, shareRepository,
                instrumentRepository, bondAnalyzer, corporateActions,
                candleService, new ReturnsCalculator(), new DividendYieldCalculator());
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
