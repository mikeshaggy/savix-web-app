package com.mikeshaggy.backend.auth.domain.jwt;

/**
 * Distinguishes the two kinds of JWT the application issues. The value is
 * carried in the {@code token_type} claim so that an access token can never be
 * used where a refresh token is expected (and vice versa).
 *
 * <p>{@link #UNKNOWN} represents a token that carries no recognizable
 * {@code token_type} claim — e.g. a token minted before this claim existed.
 * Such tokens are parsed successfully but rejected at the points that require a
 * specific type, which forces affected users to re-authenticate once.
 */
public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh"),
    UNKNOWN("unknown");

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

    /**
     * Maps a raw {@code token_type} claim to a {@link TokenType}. Never throws:
     * a missing or unrecognized value resolves to {@link #UNKNOWN} so callers
     * can decide how strictly to treat it.
     */
    public static TokenType fromClaim(String rawValue) {
        if (rawValue != null) {
            for (TokenType type : values()) {
                if (type != UNKNOWN && type.claimValue.equals(rawValue)) {
                    return type;
                }
            }
        }
        return UNKNOWN;
    }
}
