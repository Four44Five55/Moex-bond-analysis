package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Базовый инструмент MOEX. Наследники ({@link Bond}, {@link Share}) хранятся в
 * отдельных таблицах (JOINED inheritance), связанных по первичному ключу.
 */
@Entity
@Table(name = "instrument")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "instrument_type", discriminatorType = DiscriminatorType.STRING, length = 16)
@Getter
@Setter
public abstract class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sec_id", nullable = false, unique = true, length = 64)
    private String secId;

    private String isin;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "full_name")
    private String fullName;

    private String currency;
    private String board;
    private String market;

    @Column(name = "last_price")
    private Double lastPrice;

    @Column(name = "prev_price")
    private Double prevPrice;

    @Column(name = "day_change_pct")
    private Double dayChangePct;

    @Column(name = "trade_volume")
    private Double tradeVolume;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Тип инструмента (для сериализации и логики без instanceof). */
    @Transient
    public abstract InstrumentType getType();
}
