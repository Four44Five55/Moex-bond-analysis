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

    @Column(name = "face_value")
    private double faceValue;

    @Column(name = "coupon_rate")
    private double couponRate;

    @Column(name = "ytm")
    private double yieldToMaturity;

    private double duration;
    private double convexity;

    @Column(name = "coupon_frequency")
    private int couponFrequency;

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

    @Column(name = "reliability_score")
    private int reliabilityScore;

    @Column(name = "investment_grade")
    private boolean investmentGrade;

    private boolean callable;
    private boolean puttable;

    @Column(name = "list_level")
    private int listLevel;

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
