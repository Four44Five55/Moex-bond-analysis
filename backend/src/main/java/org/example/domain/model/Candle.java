package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Историческая свеча (OHLCV) инструмента за интервал. */
@Entity
@Table(name = "candle")
@Getter
@Setter
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sec_id", nullable = false, length = 64)
    private String secId;

    @Column(nullable = false, length = 32)
    private String market;

    @Column(name = "interval_code", nullable = false)
    private int intervalCode;

    @Column(nullable = false)
    private Instant ts;

    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
}
