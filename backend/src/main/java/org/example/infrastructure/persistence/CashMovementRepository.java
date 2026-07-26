package org.example.infrastructure.persistence;

import org.example.domain.model.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findByAccountIdOrderByMovedOnAscIdAsc(Long accountId);
}
