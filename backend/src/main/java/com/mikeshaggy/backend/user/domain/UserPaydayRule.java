package com.mikeshaggy.backend.user.domain;

import com.mikeshaggy.backend.common.paycycle.PaydayRule;
import com.mikeshaggy.backend.common.paycycle.WeekendShift;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * User-configured payday rule ("salary on day N, shifted around weekends"). One row per user;
 * overrides the learned rule in {@code ExpectedPaydayResolver}.
 */
@Entity
@Table(name = "user_payday_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPaydayRule {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekend_shift", nullable = false, length = 24)
    @Builder.Default
    private WeekendShift weekendShift = WeekendShift.PREVIOUS_BUSINESS_DAY;

    @UpdateTimestamp
    private Instant updatedAt;

    public PaydayRule toPaydayRule() {
        return PaydayRule.configured(dayOfMonth, weekendShift);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPaydayRule)) return false;
        return userId != null && userId.equals(((UserPaydayRule) o).getUserId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
