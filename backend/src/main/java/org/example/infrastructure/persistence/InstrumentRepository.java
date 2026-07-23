package org.example.infrastructure.persistence;

import org.example.domain.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/** Полиморфный репозиторий инструментов (для поиска по коду и общих запросов). */
public interface InstrumentRepository
        extends JpaRepository<Instrument, Long>, JpaSpecificationExecutor<Instrument> {

    Optional<Instrument> findBySecId(String secId);

    List<Instrument> findAllBySecIdIn(List<String> secIds);
}
