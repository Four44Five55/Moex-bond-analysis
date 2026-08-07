package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Облигация MOEX. Финансовые метрики (доходность, дюрация, купон) и оценка
 * надёжности эмитента. Дюрация хранится в годах.
 */
@Entity
@Table(name = "bond")
@DiscriminatorValue("BOND")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
public class Bond extends Instrument {

    @Column(name = "face_value", nullable = false)
    private double faceValue;

    @Column(name = "coupon_rate", nullable = false)
    private double couponRate;

    /**
     * Величины, которых у выпуска может <b>не быть</b>, — отсюда обёртки, а не примитивы.
     * У бумаги без сделок доходность и дюрация не определены, и ноль вместо них не нейтрален:
     * он проходит фильтр «дюрация не больше N» и всплывает в сортировке по доходности как
     * настоящее значение. С {@code null} и SQL-сравнения, и агрегаты такие выпуски пропускают.
     */
    @Column(name = "ytm")
    private Double yieldToMaturity;

    private Double duration;
    private Double convexity;

    @Column(name = "coupon_frequency")
    private Integer couponFrequency;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "next_coupon_date")
    private LocalDate nextCouponDate;

    /**
     * Накопленный купонный доход (НКД) на одну облигацию, в валюте.
     * Поле примитивное, поэтому колонка обязана быть NOT NULL DEFAULT 0 (см. V4): на NULL
     * Hibernate роняет чтение всей облигации, а не подставляет ноль.
     */
    @Column(name = "accrued_interest", nullable = false)
    private double accruedInterest;

    /** Ближайшая дата оферты (put/call/выкуп), если есть. */
    @Column(name = "offer_date")
    private LocalDate offerDate;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "issuer_rating")
    private String issuerRating;

    /** Оценка выводится из уровня листинга, а он известен не для всех выпусков. */
    @Column(name = "reliability_score")
    private Integer reliabilityScore;

    @Column(name = "investment_grade", nullable = false)
    private boolean investmentGrade;

    @Column(nullable = false)
    private boolean callable;

    @Column(nullable = false)
    private boolean puttable;

    @Column(name = "list_level")
    private Integer listLevel;

    /** Флаг аномальной доходности (сырьё MOEX иногда содержит YTM в сотни %). */
    @Column(name = "suspicious_yield", nullable = false)
    private boolean suspiciousYield;

    @Override
    public InstrumentType getType() {
        return InstrumentType.BOND;
    }

    /** Текущая рыночная цена в валюте: номинал * (цена% / 100). */
    @Transient
    public double getCurrentPrice() {
        Double pricePercent = getLastPrice();
        return pricePercent == null ? 0.0 : faceValue * (pricePercent / 100.0);
    }

    /** Годовой купонный доход: ставка% * номинал / 100. */
    @Transient
    public double getAnnualCoupon() {
        return couponRate * faceValue / 100.0;
    }

    /** Текущая доходность: годовой купон к текущей рыночной цене (в валюте), %. */
    @Transient
    public double getCurrentYield() {
        double price = getCurrentPrice();
        return price > 0 ? getAnnualCoupon() / price * 100.0 : 0.0;
    }
}
