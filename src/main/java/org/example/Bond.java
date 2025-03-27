package org.example;

import lombok.Data;
import java.time.LocalDate;

@Data
public class Bond {
    private String secId;               // Код ценной бумаги
    private String fullName;            // Полное наименование
    private String qualification;       // Требования к квалификации
    private double pricePercent;        // Цена в процентах
    private double couponRate;          // Ставка купона (% годовых)
    private double tradeVolume;         // Объем сделок за 15 дней
    private double yieldToMaturity;     // Доходность к погашению
    private double duration;            // Дюрация
    private int couponFrequency;        // Частота выплаты купонов
    private boolean isCallable;         // Возможность досрочного погашения
    private LocalDate issueDate;        // Дата выпуска
    private LocalDate maturityDate;     // Дата погашения
    private String issuerRating;        // Рейтинг надежности эмитента (AAA, BB+ и т.д.)
    private int reliabilityScore;       // Числовая оценка надежности (1-10)

    public boolean isInvestmentGrade() {
        return reliabilityScore >= 7;  // BBB- и выше
    }
}