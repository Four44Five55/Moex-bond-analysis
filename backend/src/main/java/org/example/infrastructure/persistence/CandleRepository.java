package org.example.infrastructure.persistence;

import org.example.domain.model.Candle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CandleRepository extends JpaRepository<Candle, Long> {

    List<Candle> findBySecIdAndIntervalCodeAndTsBetweenOrderByTs(
            String secId, int intervalCode, Instant from, Instant to);

    void deleteBySecIdAndIntervalCodeAndTsBetween(
            String secId, int intervalCode, Instant from, Instant to);
}
