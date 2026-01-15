package com.mikeshaggy.backend.auth.domain.ratelimit;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateLimitRepository extends CrudRepository<RateLimitEntry, String> {
}
