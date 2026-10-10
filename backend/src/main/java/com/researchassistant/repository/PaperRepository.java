package com.researchassistant.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.researchassistant.entity.Paper;

public interface PaperRepository extends JpaRepository<Paper, Long> {

    Optional<Paper> findByExternalId(String externalId);

    Optional<Paper> findByContentHash(String contentHash);

    List<Paper> findByExternalIdIn(Collection<String> externalIds);
}
