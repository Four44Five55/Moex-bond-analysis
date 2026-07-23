package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Сделка по счёту. Цена: для акций — ₽ за штуку; для облигаций — % от номинала.
 * {@code aci} — накопленный купонный доход (НКД) на одну облигацию при покупке/продаже.
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

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private double aci;

    /** Комиссия брокера по сделке в ₽ (рассчитана из % счёта или задана явно). */
    @Column(nullable = false)
    private double commission;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
