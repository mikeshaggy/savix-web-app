package com.mikeshaggy.backend.auth.repo;

import com.mikeshaggy.backend.auth.domain.reset.ResetToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ResetTokenRepository extends CrudRepository<ResetToken, String> {

    void deleteByUserId(UUID userId);
}
