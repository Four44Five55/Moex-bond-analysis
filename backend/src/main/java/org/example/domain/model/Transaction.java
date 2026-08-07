package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.domain.money.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Сделка по счёту. Цена: для акций — ₽ за штуку; для облигаций — % от номинала.
 * {@code aci} — накопленный купонный доход (НКД) на одну облигацию при покупке/продаже.
 * <p>
 * Денежные поля инициализированы нулём не для красоты: у примитивного {@code double} значение
 * по умолчанию было 0, а у {@link BigDecimal} — {@code null}, и сделка, созданная без явного
 * НКД или комиссии, падала бы на NOT NULL-ограничении при сохранении.
 */
@Entity
@Table(name = "txn")
@Getter
@Setter
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "sec_id", nullable = false, length = 64)
    private String secId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TradeSide side;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity = Money.ZERO;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price = Money.ZERO;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal aci = Money.ZERO;

    /** Комиссия брокера по сделке в ₽ (рассчитана из % счёта или задана явно). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal commission = Money.ZERO;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
