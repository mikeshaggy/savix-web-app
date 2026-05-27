package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.dto.CreateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.dto.FixedPaymentResponse;
import com.mikeshaggy.backend.fixedpayment.dto.UpdateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.maintenance.FixedPaymentOccurrenceMaintenanceService;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FixedPaymentCrudService {

    private final FixedPaymentRepository fixedPaymentRepository;
    private final FixedPaymentOccurrenceRepository occurrenceRepository;
    private final FixedPaymentOccurrenceMaintenanceService maintenanceService;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final Clock clock;

    @Transactional
    public FixedPaymentResponse createFixedPayment(CreateFixedPaymentRequest request, UUID userId) {
        LocalDate activeFrom = request.activeFrom() != null ? request.activeFrom() : LocalDate.now(clock);
        validateActiveDates(activeFrom, request.activeTo(), request.anchorDate());

        Wallet wallet = walletService.getWalletEntityByIdForUser(request.walletId(), userId);

        Category category = categoryService.getCategoryEntityByIdForUser(request.categoryId(), userId);

        FixedPayment fp = FixedPayment.builder()
                .wallet(wallet)
                .category(category)
                .title(request.title())
                .amount(request.amount())
                .anchorDate(request.anchorDate())
                .cycle(request.cycle())
                .activeFrom(activeFrom)
                .activeTo(request.activeTo())
                .notes(request.notes())
                .build();

        fp = fixedPaymentRepository.save(fp);

        maintenanceService.prepareOccurrencesAfterFixedPaymentMutation(userId);

        log.info("Fixed payment created: fixedPaymentId={}, userId={}, walletId={}, categoryId={}",
            fp.getId(), userId, wallet.getId(), category.getId());

        return FixedPaymentResponse.from(fp);
    }

    @Transactional
    public FixedPaymentResponse updateFixedPayment(Integer id, UpdateFixedPaymentRequest request, UUID userId) {

        FixedPayment fp = getFixedPaymentOrThrowForUser(id, userId);
        validateActiveDates(fp.getActiveFrom(), request.activeTo(), request.anchorDate());

        boolean amountChanged = !fp.getAmount().equals(request.amount());
        boolean cycleChanged = !fp.getCycle().equals(request.cycle());
        boolean anchorChanged = !fp.getAnchorDate().equals(request.anchorDate());
        boolean activeToShortened = isActiveToShortened(fp.getActiveTo(), request.activeTo());

        if (amountChanged || cycleChanged || anchorChanged) {
            List<FixedPaymentOccurrence> futurePending = occurrenceRepository
                    .findFuturePendingByFixedPaymentId(fp.getId(), LocalDate.now(clock));
            occurrenceRepository.deleteAll(futurePending);
        }

        if (activeToShortened) {
            List<FixedPaymentOccurrence> stalePending = occurrenceRepository
                    .findPendingAfterActiveTo(fp.getId(), request.activeTo());
            occurrenceRepository.deleteAll(stalePending);
        }

        request.applyTo(fp);

        fp = fixedPaymentRepository.save(fp);

        maintenanceService.prepareOccurrencesAfterFixedPaymentMutation(userId);

        log.info("Fixed payment updated: fixedPaymentId={}, userId={}, walletId={}, categoryId={}",
            fp.getId(), userId, fp.getWallet().getId(), fp.getCategory().getId());

        return FixedPaymentResponse.from(fp);
    }

    @Transactional
    public void deactivateFixedPayment(Integer id, UUID userId) {

        FixedPayment fp = getFixedPaymentOrThrowForUser(id, userId);

        fp.setActiveTo(LocalDate.now(clock));

        List<FixedPaymentOccurrence> futurePending = occurrenceRepository
                .findFuturePendingByFixedPaymentId(fp.getId(), LocalDate.now(clock));
        int deletedOccurrences = futurePending.size();
        occurrenceRepository.deleteAll(futurePending);

        fixedPaymentRepository.save(fp);

        log.info("Fixed payment deactivated: fixedPaymentId={}, userId={}, removedFutureOccurrences={}",
            fp.getId(), userId, deletedOccurrences);
    }

    public List<FixedPaymentResponse> getAllFixedPayments(Integer walletId, UUID userId) {

        return fixedPaymentRepository.findAllByWalletIdAndUserId(walletId, userId).stream()
                .map(FixedPaymentResponse::from)
                .toList();
    }

    private FixedPayment getFixedPaymentOrThrowForUser(Integer id, UUID userId) {
        return fixedPaymentRepository.findByIdAndWalletUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fixed payment not found with id: " + id));
    }

    private void validateActiveDates(LocalDate activeFrom, LocalDate activeTo, LocalDate anchorDate) {
        if (activeTo == null) {
            return;
        }
        if (activeFrom != null && activeTo.isBefore(activeFrom)) {
            throw new IllegalArgumentException("activeTo must not be before activeFrom");
        }
        if (anchorDate != null && activeTo.isBefore(anchorDate)) {
            throw new IllegalArgumentException("activeTo must not be before anchorDate");
        }
    }

    private boolean isActiveToShortened(LocalDate currentActiveTo, LocalDate requestedActiveTo) {
        return requestedActiveTo != null
                && (currentActiveTo == null || requestedActiveTo.isBefore(currentActiveTo));
    }
}
