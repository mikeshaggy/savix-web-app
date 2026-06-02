package com.mikeshaggy.backend.fund.service;

import com.mikeshaggy.backend.common.calculation.CalculationUtils;
import com.mikeshaggy.backend.common.exception.ConflictException;
import com.mikeshaggy.backend.fund.domain.Fund;
import com.mikeshaggy.backend.fund.domain.FundStatus;
import com.mikeshaggy.backend.fund.dto.*;
import com.mikeshaggy.backend.fund.repository.FundRepository;
import com.mikeshaggy.backend.transfer.service.TransferService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.service.UserService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FundService {

    private static final int NEAR_DEADLINE_DAYS = 60;
    private static final int TOP_FUNDS_LIMIT = 3;

    private final FundRepository fundRepository;
    private final WalletService walletService;
    private final UserService userService;
    private final TransferService transferService;
    private final Clock clock;

    public List<FundResponse> getFundsForUser(UUID userId) {
        return fundRepository.findByUserId(userId).stream()
                .map(FundResponse::from)
                .toList();
    }

    public FundResponse getFundById(Long fundId, UUID userId) {
        return FundResponse.from(getFundOrThrow(fundId, userId));
    }

    @Transactional
    public FundResponse createFund(FundCreateRequest request, UUID userId) {
        if (fundRepository.existsByUserIdAndNameIgnoreCaseAndStatus(userId, request.name(), FundStatus.ACTIVE)) {
            throw new ConflictException(
                    "An active fund named '" + request.name() + "' already exists.");
        }

        User user = userService.getUserOrThrow(userId);

        Wallet sourceWallet = null;
        if (request.sourceWalletId() != null) {
            sourceWallet = walletService.getWalletEntityByIdForUser(request.sourceWalletId(), userId);
            if (sourceWallet.isFund()) {
                throw new IllegalArgumentException("Source wallet cannot be a fund wallet.");
            }
        }

        Wallet fundWallet = walletService.createFundWallet(request.name(), user);

        Fund fund = Fund.builder()
                .user(user)
                .fundWallet(fundWallet)
                .sourceWallet(sourceWallet)
                .name(request.name())
                .description(request.description())
                .targetAmount(request.targetAmount())
                .icon(request.icon())
                .color(request.color())
                .deadlineDate(request.deadlineDate())
                .build();

        Fund saved = fundRepository.save(fund);

        log.info("Fund created: fundId={}, userId={}, name={}", saved.getId(), userId, saved.getName());

        return FundResponse.from(saved);
    }

    @Transactional
    public FundResponse updateFund(Long fundId, FundUpdateRequest request, UUID userId) {
        Fund fund = getFundOrThrow(fundId, userId);

        if (fund.getStatus() != FundStatus.ACTIVE) {
            throw new ConflictException("Only active funds can be updated.");
        }

        if (request.targetAmount() != null) {
            BigDecimal currentAmount = fund.getFundWallet().getBalance();
            if (request.targetAmount().compareTo(currentAmount) < 0) {
                throw new IllegalArgumentException(
                        "Target amount cannot be less than the current saved amount (" + currentAmount + ").");
            }
            fund.setTargetAmount(request.targetAmount());
        }

        if (request.name() != null && request.name().isBlank()) {
            throw new IllegalArgumentException("Fund name cannot be blank.");
        }

        if (request.name() != null && !request.name().equalsIgnoreCase(fund.getName())) {
            if (fundRepository.existsByUserIdAndNameIgnoreCaseAndStatus(userId, request.name(), FundStatus.ACTIVE)) {
                throw new ConflictException(
                        "An active fund named '" + request.name() + "' already exists.");
            }
            fund.setName(request.name());
            fund.getFundWallet().setName(request.name());
        }

        if (request.sourceWalletId() != null) {
            Wallet sourceWallet = walletService.getWalletEntityByIdForUser(request.sourceWalletId(), userId);
            if (sourceWallet.isFund()) {
                throw new IllegalArgumentException("Source wallet cannot be a fund wallet.");
            }
            fund.setSourceWallet(sourceWallet);
        }

        if (request.description() != null) {
            fund.setDescription(request.description());
        }
        if (request.icon() != null) {
            fund.setIcon(request.icon());
        }
        if (request.color() != null) {
            fund.setColor(request.color());
        }
        if (request.deadlineDate() != null) {
            fund.setDeadlineDate(request.deadlineDate());
        }

        Fund saved = fundRepository.save(fund);

        log.info("Fund updated: fundId={}, userId={}", fundId, userId);

        return FundResponse.from(saved);
    }

    @Transactional
    public FundResponse archiveFund(Long fundId, FundArchiveRequest request, UUID userId) {
        Fund fund = getFundOrThrow(fundId, userId);

        if (fund.getStatus() != FundStatus.ACTIVE) {
            throw new ConflictException("Only active funds can be archived.");
        }

        BigDecimal currentBalance = fund.getFundWallet().getBalance();

        if (currentBalance.compareTo(BigDecimal.ZERO) > 0) {
            if (request == null || request.returnRemainingBalance() == null) {
                throw new IllegalArgumentException(
                        "This fund has a remaining balance of " + currentBalance +
                        ". Please decide: return the balance to a wallet (returnRemainingBalance=true) " +
                        "or keep it in the fund (returnRemainingBalance=false).");
            }

            if (Boolean.TRUE.equals(request.returnRemainingBalance())) {
                if (request.returnToWalletId() == null) {
                    throw new IllegalArgumentException(
                            "returnToWalletId is required when returnRemainingBalance is true.");
                }
                Wallet returnWallet = walletService.getWalletEntityByIdForUser(
                        request.returnToWalletId(), userId);
                if (returnWallet.isFund()) {
                    throw new IllegalArgumentException("Return wallet cannot be a fund wallet.");
                }
                transferService.createFundTransfer(
                        fund.getFundWallet().getId(),
                        returnWallet.getId(),
                        currentBalance,
                        userId,
                        LocalDate.now(clock),
                        "Fund archived — balance returned");

                log.info("Fund archive transfer: fundId={}, returnWalletId={}, amount={}, userId={}",
                        fundId, returnWallet.getId(), currentBalance, userId);
            }

        }

        fund.setStatus(FundStatus.ARCHIVED);
        Fund saved = fundRepository.save(fund);

        log.info("Fund archived: fundId={}, userId={}", fundId, userId);

        return FundResponse.from(saved);
    }

    @Transactional
    public FundResponse depositToFund(Long fundId, FundDepositRequest request, UUID userId) {
        Fund fund = getFundOrThrow(fundId, userId);

        if (fund.getStatus() != FundStatus.ACTIVE) {
            throw new ConflictException("Only active funds can accept deposits.");
        }

        Wallet sourceWallet = walletService.getWalletEntityByIdForUser(request.sourceWalletId(), userId);
        if (sourceWallet.isFund()) {
            throw new IllegalArgumentException("Source wallet cannot be a fund wallet.");
        }

        transferService.createFundTransfer(
                sourceWallet.getId(),
                fund.getFundWallet().getId(),
                request.amount(),
                userId,
                request.date(),
                request.notes());

        log.info("Fund deposit: fundId={}, sourceWalletId={}, amount={}, userId={}",
                fundId, sourceWallet.getId(), request.amount(), userId);

        return FundResponse.from(getFundOrThrow(fundId, userId));
    }

    @Transactional
    public FundResponse withdrawFromFund(Long fundId, FundWithdrawRequest request, UUID userId) {
        Fund fund = getFundOrThrow(fundId, userId);

        BigDecimal currentBalance = fund.getFundWallet().getBalance();
        if (request.amount().compareTo(currentBalance) > 0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount " + request.amount() +
                    " exceeds fund balance " + currentBalance + ".");
        }

        Wallet destinationWallet = walletService.getWalletEntityByIdForUser(request.destinationWalletId(), userId);
        if (destinationWallet.isFund()) {
            throw new IllegalArgumentException("Destination wallet cannot be a fund wallet.");
        }

        transferService.createFundTransfer(
                fund.getFundWallet().getId(),
                destinationWallet.getId(),
                request.amount(),
                userId,
                request.date(),
                request.notes());

        log.info("Fund withdrawal: fundId={}, destinationWalletId={}, amount={}, userId={}",
                fundId, destinationWallet.getId(), request.amount(), userId);

        return FundResponse.from(getFundOrThrow(fundId, userId));
    }

    public FundsSummaryDto getFundsSummary(UUID userId) {
        List<Fund> activeFunds = fundRepository.findByUserIdAndStatus(userId, FundStatus.ACTIVE);

        if (activeFunds.isEmpty()) {
            return new FundsSummaryDto(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
        }

        BigDecimal totalSaved = activeFunds.stream()
                .map(f -> f.getFundWallet().getBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTarget = activeFunds.stream()
                .map(Fund::getTargetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal overallProgressPercent = totalSaved.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalSaved.multiply(CalculationUtils.HUNDRED)
                        .divide(totalTarget, CalculationUtils.SCALE, CalculationUtils.ROUNDING);

        List<FundSummaryItemDto> topFunds = activeFunds.stream()
                .map(f -> toSummaryItem(f, null))
                .sorted(Comparator.comparing(FundSummaryItemDto::progressPercent).reversed())
                .limit(TOP_FUNDS_LIMIT)
                .toList();

        LocalDate today = LocalDate.now(clock);
        LocalDate deadline = today.plusDays(NEAR_DEADLINE_DAYS);

        List<FundSummaryItemDto> nearDeadlineFunds = activeFunds.stream()
                .filter(f -> f.getDeadlineDate() != null
                        && !f.getDeadlineDate().isBefore(today)
                        && !f.getDeadlineDate().isAfter(deadline))
                .sorted(Comparator.comparing(Fund::getDeadlineDate))
                .map(f -> toSummaryItem(f, ChronoUnit.DAYS.between(today, f.getDeadlineDate())))
                .toList();

        return new FundsSummaryDto(
                activeFunds.size(),
                totalSaved,
                totalTarget,
                overallProgressPercent,
                topFunds,
                nearDeadlineFunds
        );
    }

    private FundSummaryItemDto toSummaryItem(Fund fund, Long daysUntilDeadline) {
        BigDecimal currentAmount = fund.getFundWallet().getBalance();
        BigDecimal targetAmount = fund.getTargetAmount();

        BigDecimal progressPercent = currentAmount.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : currentAmount.multiply(CalculationUtils.HUNDRED)
                        .divide(targetAmount, CalculationUtils.SCALE, CalculationUtils.ROUNDING);

        boolean isTargetReached = currentAmount.compareTo(targetAmount) >= 0;

        return new FundSummaryItemDto(
                fund.getId(),
                fund.getName(),
                fund.getIcon(),
                fund.getColor(),
                progressPercent,
                currentAmount,
                targetAmount,
                isTargetReached,
                fund.getDeadlineDate(),
                daysUntilDeadline
        );
    }

    private Fund getFundOrThrow(Long fundId, UUID userId) {
        return fundRepository.findByIdAndUserId(fundId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Fund not found with id: " + fundId));
    }
}
