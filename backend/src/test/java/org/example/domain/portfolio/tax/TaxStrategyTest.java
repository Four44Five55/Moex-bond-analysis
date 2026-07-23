package org.example.domain.portfolio.tax;

import org.example.domain.model.Account;
import org.example.domain.model.AccountType;
import org.example.domain.model.IisType;
import org.example.domain.portfolio.RealizedSale;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TaxStrategyTest {

    private final BrokerageTaxStrategy brokerage = new BrokerageTaxStrategy();
    private final IisTypeBTaxStrategy iisB = new IisTypeBTaxStrategy();

    private Account account(AccountType type, IisType iis) {
        Account a = new Account();
        a.setType(type);
        a.setIisType(iis);
        a.setBaseTaxRate(13);
        return a;
    }

    private RealizedSale sale(double pnl, int yearsHeld) {
        LocalDate sell = LocalDate.of(2026, 1, 1);
        return new RealizedSale(1, sell.minusYears(yearsHeld), sell, 0, pnl);
    }

    @Test
    void brokerageAppliesLdvForLongHeldLots() {
        // Прибыль 800к, держали 3 года → освобождается (лимит 3млн×3); купоны и дивиденды облагаются
        TaxContext ctx = new TaxContext(account(AccountType.BROKERAGE, null),
                List.of(sale(800_000, 3)), 1000, 500);

        TaxBreakdown r = brokerage.compute(ctx);

        assertThat(r.ldvExemptGain()).isEqualTo(800_000.0);
        assertThat(r.gainTax()).isZero();
        assertThat(r.couponTax()).isCloseTo(130.0, offset(1e-6));   // 1000 * 13%
        assertThat(r.dividendTax()).isCloseTo(65.0, offset(1e-6));  // 500 * 13%
        assertThat(r.totalTax()).isCloseTo(195.0, offset(1e-6));
    }

    @Test
    void brokerageProgressiveRateAboveThreshold() {
        // Прибыль 6млн, держали 1 год (без ЛДВ): 5млн×13% + 1млн×15%
        TaxContext ctx = new TaxContext(account(AccountType.BROKERAGE, null),
                List.of(sale(6_000_000, 1)), 0, 0);

        TaxBreakdown r = brokerage.compute(ctx);

        assertThat(r.ldvExemptGain()).isZero();
        assertThat(r.gainTax()).isCloseTo(800_000.0, offset(1e-6));
    }

    @Test
    void iisTypeBExemptsGainsAndCouponsButTaxesDividends() {
        TaxContext ctx = new TaxContext(account(AccountType.IIS, IisType.B),
                List.of(sale(500_000, 1)), 1000, 500);

        TaxBreakdown r = iisB.compute(ctx);

        assertThat(r.gainTax()).isZero();
        assertThat(r.couponTax()).isZero();
        assertThat(r.dividendTax()).isCloseTo(65.0, offset(1e-6));
        assertThat(r.totalTax()).isCloseTo(65.0, offset(1e-6));
    }
}
