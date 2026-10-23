package org.example.infrastructure.persistence;

import org.example.domain.model.BondDefaultMark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BondDefaultMarkRepository extends JpaRepository<BondDefaultMark, Long> {

    List<BondDefaultMark> findByClientId(String clientId);

    Optional<BondDefaultMark> findByClientIdAndSecId(String clientId, String secId);
}
