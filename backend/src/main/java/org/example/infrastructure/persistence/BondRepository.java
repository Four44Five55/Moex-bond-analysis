package org.example.infrastructure.persistence;

import org.example.domain.model.Bond;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий облигаций. Наследует спецификации для динамических фильтров
 * ({@link org.example.domain.filter.BondSpecifications}).
 */
public interface BondRepository extends JpaRepository<Bond, Long>, JpaSpecificationExecutor<Bond> {

    Optional<Bond> findBySecId(String secId);

    List<Bond> findBySecIdIn(List<String> secIds);
}
