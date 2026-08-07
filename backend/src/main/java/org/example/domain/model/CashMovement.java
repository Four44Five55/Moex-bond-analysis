package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.domain.money.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Внешнее движение денег по счёту: пополнение или вывод. {@code amount} всегда положительна,
 * знак задаётся направлением — так нельзя случайно завести «пополнение на минус тысячу».
 */
@Entity
@Table(name = "cash_movement")
@Getter
@Setter
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CashDirection direction;

    @Column(name = "moved_on", nullable = false)
    private LocalDate movedOn;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = Money.ZERO;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Сумма со знаком: пополнение увеличивает остаток, вывод уменьшает. */
    public BigDecimal signedAmount() {
        return direction == CashDirection.OUT ? amount.negate() : amount;
    }
}
