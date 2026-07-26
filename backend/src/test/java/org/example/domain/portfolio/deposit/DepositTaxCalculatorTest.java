package org.example.domain.portfolio.deposit;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DepositTaxCalculatorTest {

    private static final double KEY_RATE = 20.0;   // вычет = 1 млн × 20% = 200 000 ₽
    private final DepositTaxCalculator calculator = new DepositTaxCalculator();

    @Test
    void incomeWithinDeductionIsNotTaxed() {
        DepositTaxBreakdown result = calculator.compute(150_000, KEY_RATE);

        assertThat(result.deduction()).isCloseTo(200_000, within(0.01));
        assertThat(result.taxableIncome()).isZero();
        assertThat(result.tax()).isZero();
    }

    @Test
    void onlyTheExcessOverDeductionIsTaxed() {
        DepositTaxBreakdown result = calculator.compute(300_000, KEY_RATE);

        assertThat(result.taxableIncome()).isCloseTo(100_000, within(0.01));
        assertThat(result.tax()).isCloseTo(13_000, within(0.01));
    }

    @Test
    void excessAboveProgressiveThresholdIsTaxedHigher() {
        // Превышение 6 млн: первые 5 млн по 13%, остаток по 15%
        DepositTaxBreakdown result = calculator.compute(6_200_000, KEY_RATE);

        assertThat(result.taxableIncome()).isCloseTo(6_000_000, within(0.01));
        assertThat(result.tax()).isCloseTo(5_000_000 * 0.13 + 1_000_000 * 0.15, within(0.01));
    }

    @Test
    void deductionScalesWithKeyRate() {
        // Один и тот же доход при разной ключевой ставке облагается по-разному —
        // ради этого ставка и вынесена в конфигурацию
        double income = 250_000;

        assertThat(calculator.compute(income, 10.0).tax()).isGreaterThan(0);
        assertThat(calculator.compute(income, 30.0).tax()).isZero();
    }

    @Test
    void notesExplainWhichDeductionWasApplied() {
        assertThat(calculator.compute(300_000, KEY_RATE).notes()).contains("превышение");
        assertThat(calculator.compute(10_000, KEY_RATE).notes()).contains("укладываются");
    }

    @Test
    void keyRateIsReportedBackSoTheDeductionCanBeExplained() {
        // Ставка приходит из ЦБ и меняется — без неё сумма вычета выглядит как магическое число
        assertThat(calculator.compute(300_000, KEY_RATE).keyRatePct()).isEqualTo(KEY_RATE);
        assertThat(calculator.compute(10_000, KEY_RATE).notes()).contains("20,00%");
    }

    @Test
    void notesFormattingDoesNotDependOnJvmLocale() {
        // Пояснение русское, значит и число в нём — с запятой, независимо от локали машины.
        // Без явной локали тот же расчёт давал бы «20.00%» на сервере с английскими настройками.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertThat(calculator.compute(10_000, KEY_RATE).notes()).contains("20,00%");
        } finally {
            Locale.setDefault(original);
        }
    }
}
