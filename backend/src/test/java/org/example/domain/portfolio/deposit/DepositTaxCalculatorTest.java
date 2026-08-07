package org.example.domain.portfolio.deposit;

import org.example.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DepositTaxCalculatorTest {

    /** Вычет = 1 млн × 20% = 200 000 ₽. */
    private static final BigDecimal KEY_RATE = Money.of("20.00");

    private final DepositTaxCalculator calculator = new DepositTaxCalculator();

    private DepositTaxBreakdown compute(String income, BigDecimal keyRate) {
        return calculator.compute(Money.of(income), keyRate);
    }

    @Test
    void incomeWithinDeductionIsNotTaxed() {
        DepositTaxBreakdown result = compute("150000", KEY_RATE);

        assertThat(result.deduction()).isEqualByComparingTo("200000");
        assertThat(result.taxableIncome()).isEqualByComparingTo("0");
        assertThat(result.tax()).isEqualByComparingTo("0");
    }

    @Test
    void onlyTheExcessOverDeductionIsTaxed() {
        DepositTaxBreakdown result = compute("300000", KEY_RATE);

        assertThat(result.taxableIncome()).isEqualByComparingTo("100000");
        assertThat(result.tax()).isEqualByComparingTo("13000");
    }

    @Test
    void excessAboveProgressiveThresholdIsTaxedHigher() {
        // Превышение 6 млн: первые 5 млн по 13%, остаток по 15%
        DepositTaxBreakdown result = compute("6200000", KEY_RATE);

        assertThat(result.taxableIncome()).isEqualByComparingTo("6000000");
        // 5 000 000 × 13% + 1 000 000 × 15%
        assertThat(result.tax()).isEqualByComparingTo("800000");
    }

    @Test
    void deductionScalesWithKeyRate() {
        // Один и тот же доход при разной ключевой ставке облагается по-разному —
        // ради этого ставка и вынесена в конфигурацию
        assertThat(compute("250000", Money.of("10.00")).tax()).isGreaterThan(Money.ZERO);
        assertThat(compute("250000", Money.of("30.00")).tax()).isEqualByComparingTo("0");
    }

    @Test
    void notesExplainWhichDeductionWasApplied() {
        assertThat(compute("300000", KEY_RATE).notes()).contains("превышение");
        assertThat(compute("10000", KEY_RATE).notes()).contains("укладываются");
    }

    @Test
    void keyRateIsReportedBackSoTheDeductionCanBeExplained() {
        // Ставка приходит из ЦБ и меняется — без неё сумма вычета выглядит как магическое число
        assertThat(compute("300000", KEY_RATE).keyRatePct()).isEqualByComparingTo(KEY_RATE);
        assertThat(compute("10000", KEY_RATE).notes()).contains("20,00%");
    }

    @Test
    void notesFormattingDoesNotDependOnJvmLocale() {
        // Пояснение русское, значит и число в нём — с запятой, независимо от локали машины.
        // Без явной локали тот же расчёт давал бы «20.00%» на сервере с английскими настройками.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertThat(compute("10000", KEY_RATE).notes()).contains("20,00%");
        } finally {
            Locale.setDefault(original);
        }
    }
}
