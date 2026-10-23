package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Отметка клиента «по бумаге выплаты не поступают с даты {@code paymentsStoppedOn}».
 * <p>
 * Платежи графика MOEX с этой даты и позже портфель полученными не считает и в прогноз
 * не ставит. Дату задаёт инвестор: биржа знает только сам факт дефолта, но не какой
 * платёж не прошёл. Отметка относится к клиенту, а не к счёту — эмитент не платит никому.
 */
@Entity
@Table(name = "bond_default_mark")
@Getter
@Setter
public class BondDefaultMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "sec_id", nullable = false, length = 64)
    private String secId;

    @Column(name = "payments_stopped_on", nullable = false)
    private LocalDate paymentsStoppedOn;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
