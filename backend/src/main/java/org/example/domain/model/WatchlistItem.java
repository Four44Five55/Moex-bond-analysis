package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Элемент анонимного списка избранного (ключ — клиентский идентификатор из заголовка). */
@Entity
@Table(name = "watchlist_item")
@Getter
@Setter
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "sec_id", nullable = false, length = 64)
    private String secId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();
}
