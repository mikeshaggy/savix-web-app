package com.mikeshaggy.backend.user.repository;

import com.mikeshaggy.backend.user.domain.UserPaydayRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserPaydayRuleRepository extends JpaRepository<UserPaydayRule, UUID> {
}
