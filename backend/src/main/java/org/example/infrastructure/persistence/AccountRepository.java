package org.example.infrastructure.persistence;

import org.example.domain.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByClientIdOrderByCreatedAt(String clientId);

    Optional<Account> findByIdAndClientId(Long id, String clientId);
}
