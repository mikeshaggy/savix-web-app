package com.mikeshaggy.backend.common.paycycle;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Result of {@link ExpectedPaydayResolver#resolve}: the expected next salary anchor and the rule that produced it.
 */
public record ExpectedPayday(LocalDate date, PaydayRule ruleUsed) {

    public ExpectedPayday {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(ruleUsed, "ruleUsed");
    }
}
