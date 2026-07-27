package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Банковский вклад. В отличие от бумаг у него нет котировок: стоимость на любую дату —
 * детерминированная функция от суммы, ставки и условий начисления
 * (см. {@code DepositCalculator}).
 */
@Entity
@Table(name = "deposit")
@Getter
@Setter
public class Deposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    /**
     * Счёт, с которого сняты деньги на вклад; {@code null} — вклад открыт деньгами со стороны.
     * <p>
     * Различие меняет расчёт внесённого капитала: перевод с брокерского счёта — движение внутри
     * портфеля, а не новые деньги, и учитывать его как внесённое значило бы посчитать одну и ту же
     * сумму дважды. Заодно тело такого вклада уходит из свободных денег счёта-источника.
     */
    @Column(name = "source_account_id")
    private Long sourceAccountId;

    @Column(nullable = false)
    private String name;

    /** Сумма вклада, ₽. */
    @Column(nullable = false)
    private double principal;

    /** Годовая ставка, %. */
    @Column(name = "rate_pct", nullable = false)
    private double ratePct;

    @Column(name = "opened_on", nullable = false)
    private LocalDate openedOn;

    @Column(name = "matures_on", nullable = false)
    private LocalDate maturesOn;

    /** true — проценты причисляются к вкладу (сложный процент); false — выплачиваются. */
    @Column(nullable = false)
    private boolean capitalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_period", nullable = false, length = 16)
    private PayoutPeriod payoutPeriod = PayoutPeriod.AT_MATURITY;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
