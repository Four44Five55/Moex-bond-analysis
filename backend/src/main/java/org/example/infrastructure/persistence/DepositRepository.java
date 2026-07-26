package org.example.infrastructure.persistence;

import org.example.domain.model.Deposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepositRepository extends JpaRepository<Deposit, Long> {

    List<Deposit> findByClientIdOrderByOpenedOnAscIdAsc(String clientId);

    Optional<Deposit> findByIdAndClientId(Long id, String clientId);
}
