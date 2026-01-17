package com.mikeshaggy.backend.auth.dto;

import com.mikeshaggy.backend.user.domain.User;

public record LoginResult(TokenPair tokens, User user) {}
