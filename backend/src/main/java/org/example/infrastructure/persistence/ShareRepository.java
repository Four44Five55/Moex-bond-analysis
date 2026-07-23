package org.example.infrastructure.persistence;

import org.example.domain.model.Share;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long>, JpaSpecificationExecutor<Share> {

    Optional<Share> findBySecId(String secId);
}
