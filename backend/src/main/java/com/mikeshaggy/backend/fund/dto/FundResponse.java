package com.mikeshaggy.backend.fund.dto;

import com.mikeshaggy.backend.common.calculation.CalculationUtils;
import com.mikeshaggy.backend.fund.domain.Fund;
import com.mikeshaggy.backend.fund.domain.FundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FundResponse(
        Long id,
        String name,
        String description,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        BigDecimal progressPercent,
        BigDecimal remainingAmount,
        boolean isTargetReached,
        String currency,
        FundStatus status,
        String icon,
        String color,
        LocalDate deadlineDate,
        Integer sourceWalletId,
        Instant createdAt,
        Instant updatedAt
) {
    public static FundResponse from(Fund fund) {
        BigDecimal currentAmount = fund.getFundWallet().getBalance();
        BigDecimal targetAmount = fund.getTargetAmount();

        BigDecimal progressPercent = currentAmount.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : currentAmount.multiply(CalculationUtils.HUNDRED)
                        .divide(targetAmount, CalculationUtils.SCALE, CalculationUtils.ROUNDING);

        BigDecimal remainingAmount = targetAmount.subtract(currentAmount).max(BigDecimal.ZERO);
        boolean isTargetReached = currentAmount.compareTo(targetAmount) >= 0;

        return new FundResponse(
                fund.getId(),
                fund.getName(),
                fund.getDescription(),
                targetAmount,
                currentAmount,
                progressPercent,
                remainingAmount,
                isTargetReached,
                fund.getCurrency(),
                fund.getStatus(),
                fund.getIcon(),
                fund.getColor(),
                fund.getDeadlineDate(),
                fund.getSourceWallet() != null ? fund.getSourceWallet().getId() : null,
                fund.getCreatedAt(),
                fund.getUpdatedAt()
        );
    }
}
