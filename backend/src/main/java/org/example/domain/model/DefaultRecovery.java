package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.domain.money.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Деньги, полученные по бумаге уже после дефолта: реструктуризация, выплата в банкротстве,
 * частичное погашение. Сумма — на всю позицию счёта, а не на одну бумагу: в банкротстве
 * платят долями требования, и перевод в «₽ на облигацию» здесь только мешает.
 * <p>
 * Это доход от вложения, а не внешнее пополнение, поэтому в {@link CashMovement} ему не место:
 * пополнение увеличивает «внесено своих денег» и выглядит для XIRR как новый капитал.
 */
@Entity
@Table(name = "default_recovery")
@Getter
@Setter
public class DefaultRecovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "sec_id", nullable = false, length = 64)
    private String secId;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = Money.ZERO;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
