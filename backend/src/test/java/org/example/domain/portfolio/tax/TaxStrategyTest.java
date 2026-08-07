package org.example.domain.portfolio.tax;

import org.example.domain.model.Account;
import org.example.domain.model.AccountType;
import org.example.domain.model.IisType;
import org.example.domain.money.Money;
import org.example.domain.portfolio.RealizedSale;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaxStrategyTest {

    private final BrokerageTaxStrategy brokerage = new BrokerageTaxStrategy();
    private final IisTypeBTaxStrategy iisB = new IisTypeBTaxStrategy();

    private Account account(AccountType type, IisType iis) {
        Account a = new Account();
        a.setType(type);
        a.setIisType(iis);
        a.setBaseTaxRate(Money.of("13"));
        return a;
    }

    private RealizedSale sale(String pnl, int yearsHeld) {
        LocalDate sell = LocalDate.of(2026, 1, 1);
        return new RealizedSale(Money.of("1"), sell.minusYears(yearsHeld), sell,
                Money.ZERO, Money.of(pnl));
    }

    @Test
    void brokerageAppliesLdvForLongHeldLots() {
        // Прибыль 800к, держали 3 года → освобождается (лимит 3млн×3); купоны и дивиденды облагаются
        TaxContext ctx = new TaxContext(account(AccountType.BROKERAGE, null),
                List.of(sale("800000", 3)), Money.of("1000"), Money.of("500"));

        TaxBreakdown r = brokerage.compute(ctx);

        assertThat(r.ldvExemptGain()).isEqualByComparingTo("800000");
        assertThat(r.gainTax()).isEqualByComparingTo("0");
        assertThat(r.couponTax()).isEqualByComparingTo("130");    // 1000 * 13%
        assertThat(r.dividendTax()).isEqualByComparingTo("65");   // 500 * 13%
        assertThat(r.totalTax()).isEqualByComparingTo("195");
    }

    @Test
    void brokerageProgressiveRateAboveThreshold() {
        // Прибыль 6млн, держали 1 год (без ЛДВ): 5млн×13% + 1млн×15%
        TaxContext ctx = new TaxContext(account(AccountType.BROKERAGE, null),
                List.of(sale("6000000", 1)), Money.ZERO, Money.ZERO);

        TaxBreakdown r = brokerage.compute(ctx);

        assertThat(r.ldvExemptGain()).isEqualByComparingTo("0");
        assertThat(r.gainTax()).isEqualByComparingTo("800000");
    }

    @Test
    void iisTypeBExemptsGainsAndCouponsButTaxesDividends() {
        TaxContext ctx = new TaxContext(account(AccountType.IIS, IisType.B),
                List.of(sale("500000", 1)), Money.of("1000"), Money.of("500"));

        TaxBreakdown r = iisB.compute(ctx);

        assertThat(r.gainTax()).isEqualByComparingTo("0");
        assertThat(r.couponTax()).isEqualByComparingTo("0");
        assertThat(r.dividendTax()).isEqualByComparingTo("65");
        assertThat(r.totalTax()).isEqualByComparingTo("65");
    }
}
