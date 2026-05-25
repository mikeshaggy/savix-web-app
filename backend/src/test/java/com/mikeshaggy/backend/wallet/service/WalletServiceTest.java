package com.mikeshaggy.backend.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.service.WalletEntryBalanceHistoryService;
import com.mikeshaggy.backend.ledger.service.WalletEntryService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.service.UserService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.dto.WalletCreateRequest;
import com.mikeshaggy.backend.wallet.dto.WalletResponse;
import com.mikeshaggy.backend.wallet.dto.WalletUpdateRequest;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserService userService;

    @Mock
    private WalletEntryService walletEntryService;

    @Mock
    private WalletEntryBalanceHistoryService walletEntryBalanceHistoryService;

    @InjectMocks
    private WalletService walletService;

    private static final UUID USER_ID = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
    }

    private Wallet wallet(Integer id, String name, String balance) {
        return Wallet.builder().id(id).name(name).balance(new BigDecimal(balance)).user(user).build();
    }

    @Nested
    class CreateWallet {

        @Test
        void happyPath_persistsWithCorrectFields() {
            // given
            WalletCreateRequest request = new WalletCreateRequest("Savings", new BigDecimal("1000.00"));
            AtomicReference<Wallet> savedWalletRef = new AtomicReference<>();

            when(userService.getUserOrThrow(USER_ID)).thenReturn(user);
            when(walletRepository.existsByUserIdAndName(USER_ID, "Savings")).thenReturn(false);
            when(walletRepository.save(any(Wallet.class)))
                    .thenAnswer(
                            inv -> {
                                Wallet w = inv.getArgument(0);
                                assertThat(w.getBalance()).isEqualByComparingTo("0");
                                w.setId(1);
                                savedWalletRef.set(w);
                                return w;
                            });
            doAnswer(
                            inv -> {
                                Wallet savedWallet = savedWalletRef.get();
                                if (savedWallet != null) {
                                    savedWallet.setBalance(new BigDecimal("1000.00"));
                                }
                                return null;
                            })
                    .when(walletEntryBalanceHistoryService)
                    .recalculateWalletLedger(1L);

            // when
            WalletResponse result = walletService.createWallet(request, USER_ID);
            // then
            assertThat(result.id()).isEqualTo(1);
            assertThat(result.name()).isEqualTo("Savings");
            assertThat(result.balance()).isEqualByComparingTo("1000.00");

            ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
            verify(walletRepository).save(captor.capture());
            assertThat(captor.getValue().getUser()).isSameAs(user);
            verify(walletEntryService)
                    .createEntry(
                            eq(savedWalletRef.get()),
                            eq(new BigDecimal("1000.00")),
                            eq(LocalDate.now()),
                            eq(SourceType.ADJUSTMENT),
                            eq(1L));
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
        }

        @Test
        void duplicateName_throws() {
            // given
            WalletCreateRequest request = new WalletCreateRequest("Main", null);

            when(userService.getUserOrThrow(USER_ID)).thenReturn(user);
            when(walletRepository.existsByUserIdAndName(USER_ID, "Main")).thenReturn(true);

            // when
            // then
            assertThatThrownBy(() -> walletService.createWallet(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(walletRepository, never()).save(any());
        }

        @Test
        void nullBalance_defaultsToZero() {
            // given
            WalletCreateRequest request = new WalletCreateRequest("Cash", null);

            when(userService.getUserOrThrow(USER_ID)).thenReturn(user);
            when(walletRepository.existsByUserIdAndName(USER_ID, "Cash")).thenReturn(false);
            when(walletRepository.save(any(Wallet.class)))
                    .thenAnswer(
                            inv -> {
                                Wallet w = inv.getArgument(0);
                                w.setId(2);
                                return w;
                            });

            // when
            WalletResponse result = walletService.createWallet(request, USER_ID);

            // then
            assertThat(result.balance()).isEqualByComparingTo("0");
            verify(walletEntryService, never()).createEntry(any(), any(), any(), any(), any());
            verify(walletEntryBalanceHistoryService, never()).recalculateWalletLedger(anyLong());
        }
    }

    @Nested
    class UpdateWalletBalance {

        @Test
        void createsAdjustmentAndRecalculates_usingProvidedEffectiveDate() {
            // given
            Wallet existing = wallet(1, "Main", "500.00");
            LocalDate effectiveDate = LocalDate.of(2026, 3, 10);

            when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(existing));
            doAnswer(
                            inv -> {
                                existing.setBalance(new BigDecimal("700.00"));
                                return null;
                            })
                    .when(walletEntryBalanceHistoryService)
                    .recalculateWalletLedger(1L);

            // when
            WalletResponse result =
                    walletService.updateWalletBalance(1, new BigDecimal("700.00"), effectiveDate, USER_ID);

            // then
            assertThat(result.balance()).isEqualByComparingTo("700.00");
            verify(walletEntryService)
                    .createEntry(
                            eq(existing),
                            eq(new BigDecimal("200.00")),
                            eq(effectiveDate),
                            eq(SourceType.ADJUSTMENT),
                            eq(1L));
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletRepository, never()).save(any());
        }
    }

    @Nested
    class UpdateWallet {

        @Test
        void renameToDuplicateName_throws() {
            // given
            Wallet existing = wallet(1, "Main", "500.00");
            WalletUpdateRequest request = new WalletUpdateRequest("Savings", null);

            when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(existing));
            when(walletRepository.existsByUserIdAndName(USER_ID, "Savings")).thenReturn(true);

            // when
            // then
            assertThatThrownBy(() -> walletService.updateWallet(1, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(walletRepository, never()).save(any());
        }

        @Test
        void keepSameName_skipsUniquenessCheck() {
            // given
            Wallet existing = wallet(1, "Main", "500.00");
            WalletUpdateRequest request = new WalletUpdateRequest("Main", null);

            when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(existing));
            when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            WalletResponse result = walletService.updateWallet(1, request, USER_ID);

            // then
            assertThat(result.balance()).isEqualByComparingTo("500.00");
            verify(walletRepository, never()).existsByUserIdAndName(any(), any());
        }

        @Test
        void balanceProvided_routesThroughLedgerAdjustmentInsteadOfDirectSave() {
            // given
            Wallet existing = wallet(1, "Main", "500.00");
            WalletUpdateRequest request = new WalletUpdateRequest("Main", new BigDecimal("1000.00"));

            when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(existing));
            doAnswer(
                            inv -> {
                                existing.setBalance(new BigDecimal("1000.00"));
                                return null;
                            })
                    .when(walletEntryBalanceHistoryService)
                    .recalculateWalletLedger(1L);

            // when
            WalletResponse result = walletService.updateWallet(1, request, USER_ID);

            // then
            assertThat(result.balance()).isEqualByComparingTo("1000.00");
            verify(walletEntryService)
                    .createEntry(
                            eq(existing),
                            eq(new BigDecimal("500.00")),
                            eq(LocalDate.now()),
                            eq(SourceType.ADJUSTMENT),
                            eq(1L));
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletRepository, never()).save(any());
        }

        @Test
        void walletNotFound_throws() {
            // given
            WalletUpdateRequest request = new WalletUpdateRequest("X", null);

            when(walletRepository.findByIdAndUserId(999, USER_ID)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> walletService.updateWallet(999, request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class DeleteWallet {

        @Test
        void notFound_throws() {
            // given
            when(walletRepository.findByIdAndUserId(999, USER_ID)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> walletService.deleteWallet(999, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(walletRepository, never()).delete(any());
        }
    }
}
