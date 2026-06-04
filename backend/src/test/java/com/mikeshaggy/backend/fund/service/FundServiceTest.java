package com.mikeshaggy.backend.fund.service;

import com.mikeshaggy.backend.common.exception.ConflictException;
import com.mikeshaggy.backend.fund.domain.Fund;
import com.mikeshaggy.backend.fund.domain.FundMovementType;
import com.mikeshaggy.backend.fund.domain.FundStatus;
import com.mikeshaggy.backend.fund.dto.*;
import com.mikeshaggy.backend.fund.repository.FundRepository;
import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.transfer.service.TransferService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.service.UserService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundServiceTest {

    @Mock
    private FundRepository fundRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private UserService userService;

    @Mock
    private TransferService transferService;

    @Mock
    private Clock clock;

    @InjectMocks
    private FundService fundService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    private User user;
    private Wallet fundWallet;
    private Wallet spendingWallet;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();

        fundWallet = Wallet.builder()
                .id(100)
                .name("Vacation")
                .user(user)
                .balance(BigDecimal.ZERO)
                .isFund(true)
                .build();

        spendingWallet = Wallet.builder()
                .id(1)
                .name("Main")
                .user(user)
                .balance(new BigDecimal("5000.00"))
                .isFund(false)
                .build();
    }

    private Fund activeFund(Long id, BigDecimal targetAmount, BigDecimal currentBalance) {
        fundWallet.setBalance(currentBalance);
        return Fund.builder()
                .id(id)
                .user(user)
                .fundWallet(fundWallet)
                .name("Vacation")
                .targetAmount(targetAmount)
                .status(FundStatus.ACTIVE)
                .build();
    }

    @Nested
    class CreateFund {

        @Test
        void happyPath_createsFundAndFundWallet() {
            FundCreateRequest request = new FundCreateRequest(
                    "Vacation", null, new BigDecimal("5000.00"), null, null, null, null);

            when(fundRepository.existsByUserIdAndNameIgnoreCaseAndStatus(USER_ID, "Vacation", FundStatus.ACTIVE))
                    .thenReturn(false);
            when(userService.getUserOrThrow(USER_ID)).thenReturn(user);
            when(walletService.createFundWallet("Vacation", user)).thenReturn(fundWallet);
            when(fundRepository.save(any(Fund.class))).thenAnswer(inv -> {
                Fund f = inv.getArgument(0);
                f.setId(1L);
                return f;
            });

            FundResponse response = fundService.createFund(request, USER_ID);

            ArgumentCaptor<Fund> captor = ArgumentCaptor.forClass(Fund.class);
            verify(fundRepository).save(captor.capture());
            Fund saved = captor.getValue();

            assertThat(saved.getFundWallet().isFund()).isTrue();
            assertThat(saved.getStatus()).isEqualTo(FundStatus.ACTIVE);
            assertThat(response.currentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.progressPercent()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.remainingAmount()).isEqualByComparingTo("5000.00");
            assertThat(response.isTargetReached()).isFalse();
        }

        @Test
        void duplicateActiveName_throwsConflictException() {
            FundCreateRequest request = new FundCreateRequest(
                    "Vacation", null, new BigDecimal("5000.00"), null, null, null, null);

            when(fundRepository.existsByUserIdAndNameIgnoreCaseAndStatus(USER_ID, "Vacation", FundStatus.ACTIVE))
                    .thenReturn(true);

            assertThatThrownBy(() -> fundService.createFund(request, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already exists");

            verify(walletService, never()).createFundWallet(any(), any());
            verify(fundRepository, never()).save(any());
        }

        @Test
        void archivedFundWithSameName_doesNotBlock() {
            FundCreateRequest request = new FundCreateRequest(
                    "Vacation", null, new BigDecimal("5000.00"), null, null, null, null);

            when(fundRepository.existsByUserIdAndNameIgnoreCaseAndStatus(USER_ID, "Vacation", FundStatus.ACTIVE))
                    .thenReturn(false);
            when(userService.getUserOrThrow(USER_ID)).thenReturn(user);
            when(walletService.createFundWallet("Vacation", user)).thenReturn(fundWallet);
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            fundService.createFund(request, USER_ID);

            verify(fundRepository).save(any(Fund.class));
        }

        @Test
        void sourceWalletNotOwnedByUser_throws() {
            FundCreateRequest request = new FundCreateRequest(
                    "Vacation", null, new BigDecimal("5000.00"), null, null, null, 999);

            when(fundRepository.existsByUserIdAndNameIgnoreCaseAndStatus(USER_ID, "Vacation", FundStatus.ACTIVE))
                    .thenReturn(false);
            when(userService.getUserOrThrow(USER_ID)).thenReturn(user);
            when(walletService.getWalletEntityByIdForUser(999, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found"));

            assertThatThrownBy(() -> fundService.createFund(request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(fundRepository, never()).save(any());
        }

        @Test
        void sourceWalletIsFundWallet_throws() {
            FundCreateRequest request = new FundCreateRequest(
                    "Vacation", null, new BigDecimal("5000.00"), null, null, null, 100);

            when(fundRepository.existsByUserIdAndNameIgnoreCaseAndStatus(USER_ID, "Vacation", FundStatus.ACTIVE))
                    .thenReturn(false);
            when(userService.getUserOrThrow(USER_ID)).thenReturn(user);
            when(walletService.getWalletEntityByIdForUser(100, USER_ID)).thenReturn(fundWallet);

            assertThatThrownBy(() -> fundService.createFund(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallet");

            verify(fundRepository, never()).save(any());
        }
    }

    @Nested
    class GetFundsForUser {

        @Test
        void returnsOnlyCurrentUsersFunds() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByUserId(USER_ID)).thenReturn(List.of(fund));

            List<FundResponse> result = fundService.getFundsForUser(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Vacation");
        }

        @Test
        void emptyList_whenNoFunds() {
            when(fundRepository.findByUserId(USER_ID)).thenReturn(List.of());

            List<FundResponse> result = fundService.getFundsForUser(USER_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetFundById {

        @Test
        void happyPath_returnsResponse() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("1000.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            FundResponse response = fundService.getFundById(1L, USER_ID);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.currentAmount()).isEqualByComparingTo("1000.00");
        }

        @Test
        void wrongUser_throwsEntityNotFoundException() {
            when(fundRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fundService.getFundById(99L, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class UpdateFund {

        @Test
        void updatesMetadata() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FundUpdateRequest request = new FundUpdateRequest(
                    null, "My holiday fund", null, "✈️", "#6366f1", null, null);

            FundResponse response = fundService.updateFund(1L, request, USER_ID);

            assertThat(response.description()).isEqualTo("My holiday fund");
            assertThat(response.emoji()).isEqualTo("✈️");
        }

        @Test
        void nameChange_alsoUpdatesFundWalletName() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(fundRepository.existsByUserIdAndNameIgnoreCaseAndStatus(USER_ID, "Japan 2027", FundStatus.ACTIVE))
                    .thenReturn(false);
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FundUpdateRequest request = new FundUpdateRequest(
                    "Japan 2027", null, null, null, null, null, null);

            fundService.updateFund(1L, request, USER_ID);

            assertThat(fund.getName()).isEqualTo("Japan 2027");
            assertThat(fund.getFundWallet().getName()).isEqualTo("Japan 2027");
        }

        @Test
        void targetBelowCurrentAmount_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("3000.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            FundUpdateRequest request = new FundUpdateRequest(
                    null, null, new BigDecimal("2000.00"), null, null, null, null);

            assertThatThrownBy(() -> fundService.updateFund(1L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target amount cannot be less");

            verify(fundRepository, never()).save(any());
        }

        @Test
        void archivedFund_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            fund.setStatus(FundStatus.ARCHIVED);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            FundUpdateRequest request = new FundUpdateRequest(
                    null, "new desc", null, null, null, null, null);

            assertThatThrownBy(() -> fundService.updateFund(1L, request, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("active funds");

            verify(fundRepository, never()).save(any());
        }

        @Test
        void blankName_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            FundUpdateRequest request = new FundUpdateRequest(
                    "   ", null, null, null, null, null, null);

            assertThatThrownBy(() -> fundService.updateFund(1L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");

            verify(fundRepository, never()).save(any());
        }

        @Test
        void emptyName_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            FundUpdateRequest request = new FundUpdateRequest(
                    "", null, null, null, null, null, null);

            assertThatThrownBy(() -> fundService.updateFund(1L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");

            verify(fundRepository, never()).save(any());
        }

        @Test
        void nullName_otherFieldsUpdate_succeeds() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FundUpdateRequest request = new FundUpdateRequest(
                    null, "Updated description", null, null, null, null, null);

            FundResponse response = fundService.updateFund(1L, request, USER_ID);

            assertThat(response.description()).isEqualTo("Updated description");
            assertThat(fund.getName()).isEqualTo("Vacation");
        }

        @Test
        void validRename_succeeds() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(fundRepository.existsByUserIdAndNameIgnoreCaseAndStatus(USER_ID, "Beach 2027", FundStatus.ACTIVE))
                    .thenReturn(false);
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FundUpdateRequest request = new FundUpdateRequest(
                    "Beach 2027", null, null, null, null, null, null);

            fundService.updateFund(1L, request, USER_ID);

            assertThat(fund.getName()).isEqualTo("Beach 2027");
        }
    }

    @Nested
    class ArchiveFund {

        @BeforeEach
        void setupClock() {
            lenient().when(clock.instant()).thenReturn(Instant.parse("2026-06-01T12:00:00Z"));
            lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        }

        @Test
        void zeroBalance_archivesWithoutBalanceDecision() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FundResponse response = fundService.archiveFund(1L, null, USER_ID);

            assertThat(response.status()).isEqualTo(FundStatus.ARCHIVED);
            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }

        @Test
        void positiveBalance_noDecision_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("1000.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            assertThatThrownBy(() -> fundService.archiveFund(1L, null, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("decide");

            verify(fundRepository, never()).save(any());
        }

        @Test
        void returnRemainingBalance_true_callsCreateFundTransfer() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("1000.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(spendingWallet);
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FundArchiveRequest request = new FundArchiveRequest(true, 1);
            FundResponse response = fundService.archiveFund(1L, request, USER_ID);

            verify(transferService).createFundTransfer(
                    eq(fundWallet.getId()),
                    eq(spendingWallet.getId()),
                    eq(new BigDecimal("1000.00")),
                    eq(USER_ID),
                    eq(TODAY),
                    any()
            );
            assertThat(response.status()).isEqualTo(FundStatus.ARCHIVED);
        }

        @Test
        void returnRemainingBalance_false_keepsBalanceAndArchives() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("1000.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FundArchiveRequest request = new FundArchiveRequest(false, null);
            FundResponse response = fundService.archiveFund(1L, request, USER_ID);

            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
            assertThat(response.status()).isEqualTo(FundStatus.ARCHIVED);
            assertThat(response.currentAmount()).isEqualByComparingTo("1000.00");
        }

        @Test
        void returnToWalletId_missing_whenReturnTrue_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("500.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            FundArchiveRequest request = new FundArchiveRequest(true, null);

            assertThatThrownBy(() -> fundService.archiveFund(1L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("returnToWalletId is required");

            verify(fundRepository, never()).save(any());
        }

        @Test
        void completedFund_canBeArchived() {
            Fund fund = activeFund(1L, new BigDecimal("1000.00"), new BigDecimal("1000.00"));
            fund.setStatus(FundStatus.COMPLETED);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FundArchiveRequest request = new FundArchiveRequest(false, null);
            FundResponse response = fundService.archiveFund(1L, request, USER_ID);

            assertThat(response.status()).isEqualTo(FundStatus.ARCHIVED);
        }

        @Test
        void alreadyArchivedFund_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            fund.setStatus(FundStatus.ARCHIVED);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            assertThatThrownBy(() -> fundService.archiveFund(1L, null, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already archived");

            verify(fundRepository, never()).save(any());
        }
    }

    @Nested
    class CompleteFund {

        @Test
        void happyPath_setsCompletedWithoutMovingMoney() {
            Fund fund = activeFund(1L, new BigDecimal("1000.00"), new BigDecimal("1000.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FundResponse response = fundService.completeFund(1L, USER_ID);

            assertThat(response.status()).isEqualTo(FundStatus.COMPLETED);
            assertThat(response.currentAmount()).isEqualByComparingTo("1000.00");
            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }

        @Test
        void overfundedFund_canBeCompleted() {
            Fund fund = activeFund(1L, new BigDecimal("1000.00"), new BigDecimal("1500.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(fundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(fundService.completeFund(1L, USER_ID).status()).isEqualTo(FundStatus.COMPLETED);
        }

        @Test
        void targetNotReached_throws() {
            Fund fund = activeFund(1L, new BigDecimal("1000.00"), new BigDecimal("400.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            assertThatThrownBy(() -> fundService.completeFund(1L, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not reached its target");

            verify(fundRepository, never()).save(any());
        }

        @Test
        void nonActiveFund_throws() {
            Fund fund = activeFund(1L, new BigDecimal("1000.00"), new BigDecimal("1000.00"));
            fund.setStatus(FundStatus.COMPLETED);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            assertThatThrownBy(() -> fundService.completeFund(1L, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("active funds can be completed");

            verify(fundRepository, never()).save(any());
        }

        @Test
        void wrongUser_throwsEntityNotFound() {
            when(fundRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fundService.completeFund(99L, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class GetFundsSummary {

        @BeforeEach
        void setupClock() {
            lenient().when(clock.instant()).thenReturn(Instant.parse("2026-06-01T12:00:00Z"));
            lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        }

        @Test
        void excludesArchivedFunds_onlyQueriesActive() {
            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE)).thenReturn(List.of());

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            assertThat(result.activeFundsCount()).isZero();
            verify(fundRepository).findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE);
            verify(fundRepository, never()).findByUserId(any());
        }

        @Test
        void noActiveFunds_returnsZerosAndEmptyLists() {
            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE)).thenReturn(List.of());

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            assertThat(result.activeFundsCount()).isZero();
            assertThat(result.totalSaved()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.totalTarget()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.overallProgressPercent()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.topFunds()).isEmpty();
            assertThat(result.nearDeadlineFunds()).isEmpty();
        }

        @Test
        void totalsDerivedFromFundWalletBalances() {
            Wallet w1 = walletWithBalance(101, new BigDecimal("2000.00"));
            Wallet w2 = walletWithBalance(102, new BigDecimal("3000.00"));

            Fund f1 = fundWithWallet(1L, w1, new BigDecimal("8000.00"));
            Fund f2 = fundWithWallet(2L, w2, new BigDecimal("7000.00"));

            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE)).thenReturn(List.of(f1, f2));

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            assertThat(result.totalSaved()).isEqualByComparingTo("5000.00");
            assertThat(result.totalTarget()).isEqualByComparingTo("15000.00");
            assertThat(result.activeFundsCount()).isEqualTo(2);
        }

        @Test
        void topFunds_sortedByProgressPercentDesc() {
            Wallet w1 = walletWithBalance(101, new BigDecimal("1000.00")); // 20%
            Wallet w2 = walletWithBalance(102, new BigDecimal("7000.00")); // 70%
            Wallet w3 = walletWithBalance(103, new BigDecimal("500.00"));  // 50%

            Fund f1 = fundWithWallet(1L, w1, new BigDecimal("5000.00"));
            Fund f2 = fundWithWallet(2L, w2, new BigDecimal("10000.00"));
            Fund f3 = fundWithWallet(3L, w3, new BigDecimal("1000.00"));

            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE))
                    .thenReturn(List.of(f1, f2, f3));

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            // f1: 1000/5000 = 20%, f2: 7000/10000 = 70%, f3: 500/1000 = 50%
            // Sorted DESC: f2(70%), f3(50%), f1(20%)
            assertThat(result.topFunds()).hasSize(3);
            assertThat(result.topFunds().get(0).id()).isEqualTo(2L); // 70%
            assertThat(result.topFunds().get(1).id()).isEqualTo(3L); // 50%
            assertThat(result.topFunds().get(2).id()).isEqualTo(1L); // 20%
        }

        @Test
        void nearDeadlineFunds_sortedByDeadlineAsc() {
            Wallet w1 = walletWithBalance(101, BigDecimal.ZERO);
            Wallet w2 = walletWithBalance(102, BigDecimal.ZERO);

            Fund f1 = fundWithWallet(1L, w1, new BigDecimal("5000.00"));
            f1.setDeadlineDate(TODAY.plusDays(45)); // within 60 days

            Fund f2 = fundWithWallet(2L, w2, new BigDecimal("5000.00"));
            f2.setDeadlineDate(TODAY.plusDays(30)); // within 60 days, earlier

            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE))
                    .thenReturn(List.of(f1, f2));

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            assertThat(result.nearDeadlineFunds()).hasSize(2);
            assertThat(result.nearDeadlineFunds().get(0).id()).isEqualTo(2L); // 30 days first
            assertThat(result.nearDeadlineFunds().get(1).id()).isEqualTo(1L); // 45 days second
            assertThat(result.nearDeadlineFunds().get(0).daysUntilDeadline()).isEqualTo(30L);
        }

        @Test
        void fundBeyondDeadlineWindow_excludedFromNearDeadline() {
            Wallet w = walletWithBalance(101, BigDecimal.ZERO);
            Fund f = fundWithWallet(1L, w, new BigDecimal("5000.00"));
            f.setDeadlineDate(TODAY.plusDays(90)); // outside 60-day window

            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE)).thenReturn(List.of(f));

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            assertThat(result.nearDeadlineFunds()).isEmpty();
        }

        @Test
        void nearDeadlineFunds_excludesPastDeadline() {
            Wallet w = walletWithBalance(101, BigDecimal.ZERO);
            Fund f = fundWithWallet(1L, w, new BigDecimal("5000.00"));
            f.setDeadlineDate(TODAY.minusDays(1)); // yesterday — overdue

            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE)).thenReturn(List.of(f));

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            assertThat(result.nearDeadlineFunds()).isEmpty();
        }

        @Test
        void nearDeadlineFunds_includesDueToday() {
            Wallet w = walletWithBalance(101, BigDecimal.ZERO);
            Fund f = fundWithWallet(1L, w, new BigDecimal("5000.00"));
            f.setDeadlineDate(TODAY); // today is boundary — must be included

            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE)).thenReturn(List.of(f));

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            assertThat(result.nearDeadlineFunds()).hasSize(1);
            assertThat(result.nearDeadlineFunds().get(0).daysUntilDeadline()).isEqualTo(0L);
        }

        @Test
        void nearDeadlineFunds_includesFundWithin60Days() {
            Wallet w = walletWithBalance(101, BigDecimal.ZERO);
            Fund f = fundWithWallet(1L, w, new BigDecimal("5000.00"));
            f.setDeadlineDate(TODAY.plusDays(60)); // exactly on boundary — included

            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE)).thenReturn(List.of(f));

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            assertThat(result.nearDeadlineFunds()).hasSize(1);
        }

        @Test
        void nearDeadlineFunds_excludesBeyond60Days() {
            Wallet w = walletWithBalance(101, BigDecimal.ZERO);
            Fund f = fundWithWallet(1L, w, new BigDecimal("5000.00"));
            f.setDeadlineDate(TODAY.plusDays(61)); // just outside window

            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE)).thenReturn(List.of(f));

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            assertThat(result.nearDeadlineFunds()).isEmpty();
        }

        @Test
        void isTargetReached_trueWhenBalanceGteTarget() {
            Wallet w = walletWithBalance(101, new BigDecimal("5000.00"));
            Fund f = fundWithWallet(1L, w, new BigDecimal("5000.00"));

            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(f));

            FundResponse response = fundService.getFundById(1L, USER_ID);

            assertThat(response.isTargetReached()).isTrue();
            assertThat(response.remainingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.progressPercent()).isEqualByComparingTo("100.00");
        }

        @Test
        void summaryItems_includeFundColor_inTopAndNearDeadline() {
            Wallet w1 = walletWithBalance(101, new BigDecimal("2000.00"));
            Wallet w2 = walletWithBalance(102, new BigDecimal("1000.00"));

            Fund f1 = fundWithWallet(1L, w1, new BigDecimal("5000.00"));
            f1.setColor("#10b981");
            f1.setDeadlineDate(TODAY.plusDays(20)); // also near-deadline

            Fund f2 = fundWithWallet(2L, w2, new BigDecimal("5000.00"));
            f2.setColor(null); // null color must be handled safely

            when(fundRepository.findByUserIdAndStatus(USER_ID, FundStatus.ACTIVE))
                    .thenReturn(List.of(f1, f2));

            FundsSummaryDto result = fundService.getFundsSummary(USER_ID);

            FundSummaryItemDto top1 = result.topFunds().stream()
                    .filter(i -> i.id().equals(1L)).findFirst().orElseThrow();
            FundSummaryItemDto top2 = result.topFunds().stream()
                    .filter(i -> i.id().equals(2L)).findFirst().orElseThrow();

            assertThat(top1.color()).isEqualTo("#10b981");
            assertThat(top2.color()).isNull();

            assertThat(result.nearDeadlineFunds()).hasSize(1);
            assertThat(result.nearDeadlineFunds().get(0).id()).isEqualTo(1L);
            assertThat(result.nearDeadlineFunds().get(0).color()).isEqualTo("#10b981");
        }
    }

    private Wallet walletWithBalance(int id, BigDecimal balance) {
        return Wallet.builder()
                .id(id)
                .name("FundWallet-" + id)
                .user(user)
                .balance(balance)
                .isFund(true)
                .build();
    }

    private Fund fundWithWallet(Long id, Wallet wallet, BigDecimal targetAmount) {
        return Fund.builder()
                .id(id)
                .user(user)
                .fundWallet(wallet)
                .name("Fund-" + id)
                .targetAmount(targetAmount)
                .status(FundStatus.ACTIVE)
                .build();
    }

    @Nested
    class DepositToFund {

        private static final LocalDate DEPOSIT_DATE = LocalDate.of(2026, 6, 1);

        @Test
        void happyPath_callsCreateFundTransferAndReturnsUpdatedResponse() {
            // before transfer: fund wallet has 0 balance
            Fund fundBefore = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            // after transfer: fund wallet has 1000 balance
            Wallet updatedFundWallet = Wallet.builder()
                    .id(100).name("Vacation").user(user)
                    .balance(new BigDecimal("1000.00")).isFund(true).build();
            Fund fundAfter = Fund.builder()
                    .id(1L).user(user).fundWallet(updatedFundWallet)
                    .name("Vacation").targetAmount(new BigDecimal("5000.00"))
                    .status(FundStatus.ACTIVE).build();

            when(fundRepository.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(Optional.of(fundBefore))
                    .thenReturn(Optional.of(fundAfter));
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(spendingWallet);

            FundDepositRequest request = new FundDepositRequest(
                    new BigDecimal("1000.00"), 1, DEPOSIT_DATE, "Monthly saving");

            FundResponse response = fundService.depositToFund(1L, request, USER_ID);

            verify(transferService).createFundTransfer(
                    eq(spendingWallet.getId()),
                    eq(fundWallet.getId()),
                    eq(new BigDecimal("1000.00")),
                    eq(USER_ID),
                    eq(DEPOSIT_DATE),
                    eq("Monthly saving")
            );
            assertThat(response.currentAmount()).isEqualByComparingTo("1000.00");
            assertThat(response.progressPercent()).isEqualByComparingTo("20.00");
            assertThat(response.remainingAmount()).isEqualByComparingTo("4000.00");
            assertThat(response.isTargetReached()).isFalse();
        }

        @Test
        void depositReachesTarget_isTargetReachedTrue() {
            Fund fundBefore = activeFund(1L, new BigDecimal("1000.00"), BigDecimal.ZERO);
            Wallet updatedFundWallet = Wallet.builder()
                    .id(100).name("Vacation").user(user)
                    .balance(new BigDecimal("1000.00")).isFund(true).build();
            Fund fundAfter = Fund.builder()
                    .id(1L).user(user).fundWallet(updatedFundWallet)
                    .name("Vacation").targetAmount(new BigDecimal("1000.00"))
                    .status(FundStatus.ACTIVE).build();

            when(fundRepository.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(Optional.of(fundBefore))
                    .thenReturn(Optional.of(fundAfter));
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(spendingWallet);

            FundDepositRequest request = new FundDepositRequest(
                    new BigDecimal("1000.00"), 1, DEPOSIT_DATE, null);

            FundResponse response = fundService.depositToFund(1L, request, USER_ID);

            assertThat(response.isTargetReached()).isTrue();
            assertThat(response.remainingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.progressPercent()).isEqualByComparingTo("100.00");
        }

        @Test
        void depositToArchivedFund_throwsConflictException() {
            Fund archived = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            archived.setStatus(FundStatus.ARCHIVED);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(archived));

            FundDepositRequest request = new FundDepositRequest(
                    new BigDecimal("500.00"), 1, DEPOSIT_DATE, null);

            assertThatThrownBy(() -> fundService.depositToFund(1L, request, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("active");

            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }

        @Test
        void depositToWrongUserFund_throwsEntityNotFoundException() {
            when(fundRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

            FundDepositRequest request = new FundDepositRequest(
                    new BigDecimal("500.00"), 1, DEPOSIT_DATE, null);

            assertThatThrownBy(() -> fundService.depositToFund(99L, request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }

        @Test
        void sourceWalletNotOwnedByUser_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(walletService.getWalletEntityByIdForUser(999, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found"));

            FundDepositRequest request = new FundDepositRequest(
                    new BigDecimal("500.00"), 999, DEPOSIT_DATE, null);

            assertThatThrownBy(() -> fundService.depositToFund(1L, request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }

        @Test
        void sourceWalletIsFundWallet_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(walletService.getWalletEntityByIdForUser(100, USER_ID)).thenReturn(fundWallet);

            FundDepositRequest request = new FundDepositRequest(
                    new BigDecimal("500.00"), 100, DEPOSIT_DATE, null);

            assertThatThrownBy(() -> fundService.depositToFund(1L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallet");

            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class WithdrawFromFund {

        private static final LocalDate WITHDRAW_DATE = LocalDate.of(2026, 6, 1);

        @Test
        void happyPath_callsCreateFundTransferAndReturnsUpdatedResponse() {
            Fund fundBefore = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("2000.00"));
            Wallet updatedFundWallet = Wallet.builder()
                    .id(100).name("Vacation").user(user)
                    .balance(new BigDecimal("1500.00")).isFund(true).build();
            Fund fundAfter = Fund.builder()
                    .id(1L).user(user).fundWallet(updatedFundWallet)
                    .name("Vacation").targetAmount(new BigDecimal("5000.00"))
                    .status(FundStatus.ACTIVE).build();

            when(fundRepository.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(Optional.of(fundBefore))
                    .thenReturn(Optional.of(fundAfter));
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(spendingWallet);

            FundWithdrawRequest request = new FundWithdrawRequest(
                    new BigDecimal("500.00"), 1, WITHDRAW_DATE, "Emergency");

            FundResponse response = fundService.withdrawFromFund(1L, request, USER_ID);

            verify(transferService).createFundTransfer(
                    eq(fundWallet.getId()),
                    eq(spendingWallet.getId()),
                    eq(new BigDecimal("500.00")),
                    eq(USER_ID),
                    eq(WITHDRAW_DATE),
                    eq("Emergency")
            );
            assertThat(response.currentAmount()).isEqualByComparingTo("1500.00");
        }

        @Test
        void withdrawDropsBelowTarget_isTargetReachedFalse() {
            // Fund was at target, withdrawal drops below
            Fund fundBefore = activeFund(1L, new BigDecimal("1000.00"), new BigDecimal("1000.00"));
            Wallet updatedFundWallet = Wallet.builder()
                    .id(100).name("Vacation").user(user)
                    .balance(new BigDecimal("800.00")).isFund(true).build();
            Fund fundAfter = Fund.builder()
                    .id(1L).user(user).fundWallet(updatedFundWallet)
                    .name("Vacation").targetAmount(new BigDecimal("1000.00"))
                    .status(FundStatus.ACTIVE).build();

            when(fundRepository.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(Optional.of(fundBefore))
                    .thenReturn(Optional.of(fundAfter));
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(spendingWallet);

            FundWithdrawRequest request = new FundWithdrawRequest(
                    new BigDecimal("200.00"), 1, WITHDRAW_DATE, null);

            FundResponse response = fundService.withdrawFromFund(1L, request, USER_ID);

            assertThat(response.isTargetReached()).isFalse();
            assertThat(response.remainingAmount()).isEqualByComparingTo("200.00");
        }

        @Test
        void withdrawFromArchivedFund_succeeds() {
            // Withdrawal from ARCHIVED is explicitly allowed for balance recovery
            Fund archivedBefore = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("800.00"));
            archivedBefore.setStatus(FundStatus.ARCHIVED);

            Wallet updatedFundWallet = Wallet.builder()
                    .id(100).name("Vacation").user(user)
                    .balance(BigDecimal.ZERO).isFund(true).build();
            Fund archivedAfter = Fund.builder()
                    .id(1L).user(user).fundWallet(updatedFundWallet)
                    .name("Vacation").targetAmount(new BigDecimal("5000.00"))
                    .status(FundStatus.ARCHIVED).build();

            when(fundRepository.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(Optional.of(archivedBefore))
                    .thenReturn(Optional.of(archivedAfter));
            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(spendingWallet);

            FundWithdrawRequest request = new FundWithdrawRequest(
                    new BigDecimal("800.00"), 1, WITHDRAW_DATE, null);

            FundResponse response = fundService.withdrawFromFund(1L, request, USER_ID);

            verify(transferService).createFundTransfer(any(), any(), any(), any(), any(), any());
            assertThat(response.status()).isEqualTo(FundStatus.ARCHIVED);
        }

        @Test
        void withdrawExceedsFundBalance_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("300.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            FundWithdrawRequest request = new FundWithdrawRequest(
                    new BigDecimal("500.00"), 1, WITHDRAW_DATE, null);

            assertThatThrownBy(() -> fundService.withdrawFromFund(1L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds fund balance");

            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }

        @Test
        void withdrawFromWrongUserFund_throwsEntityNotFoundException() {
            when(fundRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

            FundWithdrawRequest request = new FundWithdrawRequest(
                    new BigDecimal("100.00"), 1, WITHDRAW_DATE, null);

            assertThatThrownBy(() -> fundService.withdrawFromFund(99L, request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }

        @Test
        void destinationWalletNotOwnedByUser_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("1000.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(walletService.getWalletEntityByIdForUser(999, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found"));

            FundWithdrawRequest request = new FundWithdrawRequest(
                    new BigDecimal("500.00"), 999, WITHDRAW_DATE, null);

            assertThatThrownBy(() -> fundService.withdrawFromFund(1L, request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }

        @Test
        void destinationWalletIsFundWallet_throws() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("1000.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(walletService.getWalletEntityByIdForUser(100, USER_ID)).thenReturn(fundWallet);

            FundWithdrawRequest request = new FundWithdrawRequest(
                    new BigDecimal("500.00"), 100, WITHDRAW_DATE, null);

            assertThatThrownBy(() -> fundService.withdrawFromFund(1L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallet");

            verify(transferService, never()).createFundTransfer(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class GetFundMovements {

        private static final LocalDate D1 = LocalDate.of(2026, 5, 25);
        private static final LocalDate D2 = LocalDate.of(2026, 5, 27);

        // Builds a transfer between the fund wallet (id 100) and the spending wallet (id 1).
        private Transfer transfer(Long id, Wallet from, Wallet to, String amount, LocalDate date, String notes) {
            return Transfer.builder()
                    .id(id)
                    .fromWallet(from)
                    .toWallet(to)
                    .amount(new BigDecimal(amount))
                    .transferDate(date)
                    .notes(notes)
                    .build();
        }

        @Test
        void depositMappedAsDeposit_withSpendingWalletAsCounterparty() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("500.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            // spending -> fund == deposit (fund wallet is the toWallet)
            Transfer deposit = transfer(88L, spendingWallet, fundWallet, "500.00", D1, "Monthly savings");
            when(transferService.findFundMovements(eq(USER_ID), eq(100), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(deposit)));

            FundMovementPageResponse result = fundService.getFundMovements(1L, USER_ID, 0, 10);

            assertThat(result.content()).hasSize(1);
            FundMovementResponse m = result.content().get(0);
            assertThat(m.type()).isEqualTo(FundMovementType.DEPOSIT);
            assertThat(m.amount()).isEqualByComparingTo("500.00");
            assertThat(m.transferId()).isEqualTo(88L);
            assertThat(m.notes()).isEqualTo("Monthly savings");
            // counterparty is the normal spending wallet, never the fund wallet
            assertThat(m.counterpartyWalletId()).isEqualTo(1);
            assertThat(m.counterpartyWalletName()).isEqualTo("Main");
        }

        @Test
        void withdrawalMappedAsWithdrawal_withSpendingWalletAsCounterparty() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("500.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            // fund -> spending == withdrawal (fund wallet is the fromWallet)
            Transfer withdrawal = transfer(90L, fundWallet, spendingWallet, "200.00", D2, "Emergency");
            when(transferService.findFundMovements(eq(USER_ID), eq(100), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(withdrawal)));

            FundMovementResponse m = fundService.getFundMovements(1L, USER_ID, 0, 10).content().get(0);

            assertThat(m.type()).isEqualTo(FundMovementType.WITHDRAWAL);
            assertThat(m.counterpartyWalletId()).isEqualTo(1);
            assertThat(m.counterpartyWalletName()).isEqualTo("Main");
        }

        @Test
        void counterpartyIsNeverTheFundWallet() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("500.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            Transfer deposit = transfer(88L, spendingWallet, fundWallet, "500.00", D1, null);
            Transfer withdrawal = transfer(90L, fundWallet, spendingWallet, "200.00", D2, null);
            when(transferService.findFundMovements(eq(USER_ID), eq(100), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(withdrawal, deposit)));

            FundMovementPageResponse result = fundService.getFundMovements(1L, USER_ID, 0, 10);

            // fund wallet id (100) must never surface as a counterparty
            assertThat(result.content()).allSatisfy(m ->
                    assertThat(m.counterpartyWalletId()).isEqualTo(1));
        }

        @Test
        void requestsNewestFirstSort() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("500.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(transferService.findFundMovements(eq(USER_ID), eq(100), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            fundService.getFundMovements(1L, USER_ID, 0, 10);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(transferService).findFundMovements(eq(USER_ID), eq(100), captor.capture());
            Sort.Order primary = captor.getValue().getSort().toList().get(0);
            assertThat(primary.getProperty()).isEqualTo("transferDate");
            assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void archivedFund_stillReturnsMovements() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("500.00"));
            fund.setStatus(FundStatus.ARCHIVED);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            Transfer withdrawal = transfer(90L, fundWallet, spendingWallet, "200.00", D2, null);
            when(transferService.findFundMovements(eq(USER_ID), eq(100), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(withdrawal)));

            FundMovementPageResponse result = fundService.getFundMovements(1L, USER_ID, 0, 10);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).type()).isEqualTo(FundMovementType.WITHDRAWAL);
        }

        @Test
        void emptyHistory_returnsEmptyPage_not404() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), BigDecimal.ZERO);
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));
            when(transferService.findFundMovements(eq(USER_ID), eq(100), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            FundMovementPageResponse result = fundService.getFundMovements(1L, USER_ID, 0, 10);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }

        @Test
        void paginationMetadataPropagated() {
            Fund fund = activeFund(1L, new BigDecimal("5000.00"), new BigDecimal("500.00"));
            when(fundRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(fund));

            Transfer deposit = transfer(88L, spendingWallet, fundWallet, "500.00", D1, null);
            Pageable pageable = PageRequest.of(0, 1);
            // 1 of 3 total elements -> 3 pages, has next, no previous
            when(transferService.findFundMovements(eq(USER_ID), eq(100), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(deposit), pageable, 3));

            FundMovementPageResponse result = fundService.getFundMovements(1L, USER_ID, 0, 1);

            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.totalElements()).isEqualTo(3);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.hasNext()).isTrue();
            assertThat(result.hasPrevious()).isFalse();
        }

        @Test
        void wrongUserFund_throwsEntityNotFound() {
            when(fundRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fundService.getFundMovements(99L, USER_ID, 0, 10))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(transferService, never()).findFundMovements(any(), any(), any());
        }
    }
}
