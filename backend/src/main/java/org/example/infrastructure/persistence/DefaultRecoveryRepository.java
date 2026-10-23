package org.example.infrastructure.persistence;

import org.example.domain.model.DefaultRecovery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DefaultRecoveryRepository extends JpaRepository<DefaultRecovery, Long> {

    List<DefaultRecovery> findByAccountIdOrderByReceivedOnAscIdAsc(Long accountId);
}
