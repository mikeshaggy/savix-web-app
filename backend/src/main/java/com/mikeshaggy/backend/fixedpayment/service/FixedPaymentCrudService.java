package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.dto.CreateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.dto.FixedPaymentResponse;
import com.mikeshaggy.backend.fixedpayment.dto.UpdateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.repo.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repo.FixedPaymentRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FixedPaymentCrudService {

    private final FixedPaymentRepository fixedPaymentRepository;
    private final FixedPaymentOccurrenceRepository occurrenceRepository;
    private final FixedPaymentOccurrenceGenerationService generationService;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final Clock clock;

    @Transactional
    public FixedPaymentResponse createFixedPayment(CreateFixedPaymentRequest request, UUID userId) {

        Wallet wallet = walletService.getWalletEntityByIdForUser(request.walletId(), userId);

        Category category = categoryService.getCategoryEntityByIdForUser(request.categoryId(), userId);

        FixedPayment fp = FixedPayment.builder()
                .wallet(wallet)
                .category(category)
                .title(request.title())
                .amount(request.amount())
                .anchorDate(request.anchorDate())
                .cycle(request.cycle())
                .activeFrom(request.activeFrom() != null ? request.activeFrom() : LocalDate.now(clock))
                .activeTo(request.activeTo())
                .notes(request.notes())
                .build();

        fp = fixedPaymentRepository.save(fp);

        generationService.ensureOccurrencesGenerated(userId);

        return FixedPaymentResponse.from(fp);
    }

    @Transactional
    public FixedPaymentResponse updateFixedPayment(Integer id, UpdateFixedPaymentRequest request, UUID userId) {

        FixedPayment fp = getFixedPaymentOrThrowForUser(id, userId);

        boolean amountChanged = !fp.getAmount().equals(request.amount());
        boolean cycleChanged = !fp.getCycle().equals(request.cycle());
        boolean anchorChanged = !fp.getAnchorDate().equals(request.anchorDate());

        if (amountChanged || cycleChanged || anchorChanged) {
            List<FixedPaymentOccurrence> futurePending = occurrenceRepository
                    .findFuturePendingByFixedPaymentId(fp.getId(), LocalDate.now(clock));
            occurrenceRepository.deleteAll(futurePending);
        }

        request.applyTo(fp);

        fp = fixedPaymentRepository.save(fp);

        if (amountChanged || cycleChanged || anchorChanged) {
            generationService.ensureOccurrencesGenerated(userId);
        }

        return FixedPaymentResponse.from(fp);
    }

    @Transactional
    public void deactivateFixedPayment(Integer id, UUID userId) {

        FixedPayment fp = getFixedPaymentOrThrowForUser(id, userId);

        fp.setActiveTo(LocalDate.now(clock));

        List<FixedPaymentOccurrence> futurePending = occurrenceRepository
                .findFuturePendingByFixedPaymentId(fp.getId(), LocalDate.now(clock));
        occurrenceRepository.deleteAll(futurePending);

        fixedPaymentRepository.save(fp);
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
}
