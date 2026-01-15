package com.mikeshaggy.backend.auth.domain.session;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends CrudRepository<RefreshSession, String> {

    void deleteByUserId(UUID userId);
}
