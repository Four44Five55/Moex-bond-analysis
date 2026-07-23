package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Инвестиционный счёт пользователя (анонимный, привязан к client_id из заголовка X-Client-Id).
 * Тип счёта и {@link IisType} определяют налоговую стратегию.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountType type = AccountType.BROKERAGE;

    /** Заполняется только для type = IIS. */
    @Enumerated(EnumType.STRING)
    @Column(name = "iis_type", length = 4)
    private IisType iisType;

    /** Комиссия брокера по умолчанию, % от суммы сделки. */
    @Column(name = "default_commission_pct", nullable = false)
    private double defaultCommissionPct;

    /** Базовая ставка НДФЛ, %. */
    @Column(name = "base_tax_rate", nullable = false)
    private double baseTaxRate = 13.0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
