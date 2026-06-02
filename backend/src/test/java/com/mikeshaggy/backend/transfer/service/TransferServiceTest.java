package com.mikeshaggy.backend.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.transfer.dto.TransferCreateRequest;
import com.mikeshaggy.backend.transfer.dto.TransferResponse;
import com.mikeshaggy.backend.transfer.dto.TransferUpdateRequest;
import com.mikeshaggy.backend.transfer.repository.TransferRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletBalanceService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private WalletBalanceService walletBalanceService;

    @InjectMocks
    private TransferService transferService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    private User user;
    private Wallet fromWallet;
    private Wallet toWallet;
    private Wallet fundWallet;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
        fromWallet =
                Wallet.builder()
                        .id(1)
                        .name("Checking")
                        .balance(new BigDecimal("2000.00"))
                        .user(user)
                        .build();
        toWallet =
                Wallet.builder()
                        .id(2)
                        .name("Savings")
                        .balance(new BigDecimal("5000.00"))
                        .user(user)
                        .build();
        fundWallet =
                Wallet.builder()
                        .id(7)
                        .name("Emergency Fund")
                        .balance(new BigDecimal("0.00"))
                        .isFund(true)
                        .user(user)
                        .build();
    }

    @Nested
    class CreateTransfer {

        @Test
        void createsTransfer_savesAndAppliesBalance() {
            // given
            TransferCreateRequest request =
                    new TransferCreateRequest(1, 2, new BigDecimal("500.00"), DATE, "Monthly savings");

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(fromWallet);
            when(walletService.getWalletEntityByIdForUser(2, USER_ID)).thenReturn(toWallet);
            when(transferRepository.save(any(Transfer.class)))
                    .thenAnswer(
                            inv -> {
                                Transfer t = inv.getArgument(0);
                                t.setId(50L);
                                return t;
                            });

            // when
            TransferResponse response = transferService.createTransfer(request, USER_ID);

            // then
            assertThat(response.amount()).isEqualByComparingTo("500.00");
            assertThat(response.fromWalletId()).isEqualTo(1);
            assertThat(response.toWalletId()).isEqualTo(2);

            verify(walletBalanceService)
                    .applyTransfer(
                            eq(1), eq(2), eq(new BigDecimal("500.00")), eq(USER_ID), eq(50L), eq(DATE));
        }

        @Test
        void selfTransfer_throws() {
            // given
            TransferCreateRequest request =
                    new TransferCreateRequest(1, 1, new BigDecimal("100.00"), DATE, null);

            // when
            // then
            assertThatThrownBy(() -> transferService.createTransfer(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same wallet");
        }

        @Test
        void walletNotOwnedByUser_throws() {
            // given
            TransferCreateRequest request =
                    new TransferCreateRequest(1, 2, new BigDecimal("500.00"), DATE, null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found with id: 1"));

            // when
            // then
            assertThatThrownBy(() -> transferService.createTransfer(request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
            verifyNoInteractions(walletBalanceService);
        }
    }

    @Nested
    class UpdateTransfer {

        @Test
        void updateAmount_sameWallets() {
            // given
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(1, 2, new BigDecimal("750.00"), DATE, "Updated amount");

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            TransferResponse response = transferService.updateTransfer(50L, request, USER_ID);

            // then
            assertThat(response.amount()).isEqualByComparingTo("750.00");

            verify(walletBalanceService)
                    .adjustForTransferEdit(
                            eq(fromWallet),
                            eq(toWallet),
                            eq(new BigDecimal("500.00")),
                            eq(fromWallet),
                            eq(toWallet),
                            eq(new BigDecimal("750.00")),
                            eq(50L),
                            eq(DATE),
                            eq(DATE));
        }

        @Test
        void updateDateOnlyPassesOldAndNewDatesToLedgerAdjustment() {
            // given
            LocalDate newDate = DATE.plusDays(5);
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(1, 2, new BigDecimal("500.00"), newDate, "Moved date");

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            transferService.updateTransfer(50L, request, USER_ID);

            // then
            verify(walletBalanceService)
                    .adjustForTransferEdit(
                            eq(fromWallet),
                            eq(toWallet),
                            eq(new BigDecimal("500.00")),
                            eq(fromWallet),
                            eq(toWallet),
                            eq(new BigDecimal("500.00")),
                            eq(50L),
                            eq(DATE),
                            eq(newDate));
        }

        @Test
        void updateWithDifferentFromWallet() {
            // given
            Wallet newFrom =
                    Wallet.builder()
                            .id(3)
                            .name("Business")
                            .balance(new BigDecimal("10000"))
                            .user(user)
                            .build();

            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(3, 2, new BigDecimal("500.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(walletService.getWalletEntityByIdForUser(3, USER_ID)).thenReturn(newFrom);
            when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            transferService.updateTransfer(50L, request, USER_ID);

            // then
            verify(walletBalanceService)
                    .adjustForTransferEdit(
                            eq(fromWallet),
                            eq(toWallet),
                            eq(new BigDecimal("500.00")),
                            eq(newFrom),
                            eq(toWallet),
                            eq(new BigDecimal("500.00")),
                            eq(50L),
                            eq(DATE),
                            eq(DATE));
        }

        @Test
        void updateWithDifferentToWallet() {
            // given
            Wallet newTo =
                    Wallet.builder()
                            .id(3)
                            .name("Investment")
                            .balance(new BigDecimal("8000"))
                            .user(user)
                            .build();

            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(1, 3, new BigDecimal("500.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(walletService.getWalletEntityByIdForUser(3, USER_ID)).thenReturn(newTo);
            when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            transferService.updateTransfer(50L, request, USER_ID);

            // then
            verify(walletBalanceService)
                    .adjustForTransferEdit(
                            eq(fromWallet),
                            eq(toWallet),
                            eq(new BigDecimal("500.00")),
                            eq(fromWallet),
                            eq(newTo),
                            eq(new BigDecimal("500.00")),
                            eq(50L),
                            eq(DATE),
                            eq(DATE));
        }

        @Test
        void selfTransferOnUpdate_throws() {
            // given
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(1, 1, new BigDecimal("500.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));

            // when
            // then
            assertThatThrownBy(() -> transferService.updateTransfer(50L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same wallet");
        }

        @Test
        void updateWithBothWalletsChanged() {
            // given
            Wallet newFrom =
                    Wallet.builder()
                            .id(3)
                            .name("Business")
                            .balance(new BigDecimal("10000"))
                            .user(user)
                            .build();
            Wallet newTo =
                    Wallet.builder()
                            .id(4)
                            .name("Investment")
                            .balance(new BigDecimal("8000"))
                            .user(user)
                            .build();

            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(3, 4, new BigDecimal("500.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(walletService.getWalletEntityByIdForUser(3, USER_ID)).thenReturn(newFrom);
            when(walletService.getWalletEntityByIdForUser(4, USER_ID)).thenReturn(newTo);
            when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            TransferResponse response = transferService.updateTransfer(50L, request, USER_ID);

            // then
            assertThat(response.fromWalletId()).isEqualTo(3);
            assertThat(response.toWalletId()).isEqualTo(4);

            verify(walletBalanceService)
                    .adjustForTransferEdit(
                            eq(fromWallet),
                            eq(toWallet),
                            eq(new BigDecimal("500.00")),
                            eq(newFrom),
                            eq(newTo),
                            eq(new BigDecimal("500.00")),
                            eq(50L),
                            eq(DATE),
                            eq(DATE));
        }

        @Test
        void updateAmountAndWalletsSimultaneously() {
            // given
            Wallet newFrom =
                    Wallet.builder()
                            .id(3)
                            .name("Business")
                            .balance(new BigDecimal("10000"))
                            .user(user)
                            .build();
            Wallet newTo =
                    Wallet.builder()
                            .id(4)
                            .name("Investment")
                            .balance(new BigDecimal("8000"))
                            .user(user)
                            .build();

            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(3, 4, new BigDecimal("1200.00"), DATE, "Changed everything");

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(walletService.getWalletEntityByIdForUser(3, USER_ID)).thenReturn(newFrom);
            when(walletService.getWalletEntityByIdForUser(4, USER_ID)).thenReturn(newTo);
            when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            TransferResponse response = transferService.updateTransfer(50L, request, USER_ID);

            // then
            assertThat(response.amount()).isEqualByComparingTo("1200.00");
            assertThat(response.fromWalletId()).isEqualTo(3);
            assertThat(response.toWalletId()).isEqualTo(4);

            verify(walletBalanceService)
                    .adjustForTransferEdit(
                            eq(fromWallet),
                            eq(toWallet),
                            eq(new BigDecimal("500.00")),
                            eq(newFrom),
                            eq(newTo),
                            eq(new BigDecimal("1200.00")),
                            eq(50L),
                            eq(DATE),
                            eq(DATE));
        }

        @Test
        void transferNotFound_throws() {
            // given
            TransferUpdateRequest request =
                    new TransferUpdateRequest(1, 2, new BigDecimal("100"), DATE, null);

            when(transferRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> transferService.updateTransfer(999L, request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class DeleteTransfer {

        @Test
        void deletesAndReversesBalance() {
            // given
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));

            // when
            transferService.deleteTransfer(50L, USER_ID);

            // then
            verify(walletBalanceService)
                    .reverseTransfer(
                            eq(1), eq(2), eq(new BigDecimal("500.00")), eq(USER_ID), eq(50L), eq(DATE));
            verify(transferRepository).delete(existing);
        }

        @Test
        void transferNotFound_throws() {
            // given
            when(transferRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> transferService.deleteTransfer(999L, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class ReadTransfer {

        @Test
        void getById_returnsResponse() {
            // given
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));

            // when
            TransferResponse response = transferService.getTransferByIdForUser(50L, USER_ID);

            // then
            assertThat(response.id()).isEqualTo(50L);
            assertThat(response.fromWalletId()).isEqualTo(1);
            assertThat(response.toWalletId()).isEqualTo(2);
            assertThat(response.amount()).isEqualByComparingTo("500.00");
        }

        @Test
        void getById_notFound_throws() {
            // given
            when(transferRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(() -> transferService.getTransferByIdForUser(999L, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void getAllForUser_returnsList() {
            // given
            Transfer t1 =
                    Transfer.builder()
                            .id(1L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("100"))
                            .transferDate(DATE)
                            .build();
            Transfer t2 =
                    Transfer.builder()
                            .id(2L)
                            .fromWallet(toWallet)
                            .toWallet(fromWallet)
                            .amount(new BigDecimal("200"))
                            .transferDate(DATE)
                            .build();

            when(transferRepository.findAllByUserId(USER_ID)).thenReturn(List.of(t1, t2));

            // when
            List<TransferResponse> responses = transferService.getTransfersForUser(USER_ID);

            // then
            assertThat(responses).hasSize(2);
        }

        @Test
        void getByWalletId_returnsList() {
            // given
            Transfer t1 =
                    Transfer.builder()
                            .id(1L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("300"))
                            .transferDate(DATE)
                            .build();

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(fromWallet);
            when(transferRepository.findByWalletIdAndUserId(1, USER_ID)).thenReturn(List.of(t1));

            // when
            List<TransferResponse> responses = transferService.getTransfersByWalletIdForUser(1, USER_ID);

            // then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).amount()).isEqualByComparingTo("300");
        }

        @Test
        void getByWalletId_walletNotOwned_throws() {
            // given
            when(walletService.getWalletEntityByIdForUser(99, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found with id: 99"));

            // when
            // then
            assertThatThrownBy(() -> transferService.getTransfersByWalletIdForUser(99, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class FundWalletGuard {

        @Test
        void createTransfer_fromFundWallet_rejected() {
            // given — regular transfer using a fund wallet as the source must be blocked
            TransferCreateRequest request =
                    new TransferCreateRequest(7, 2, new BigDecimal("100.00"), DATE, null);

            when(walletService.getWalletEntityByIdForUser(7, USER_ID)).thenReturn(fundWallet);
            when(walletService.getWalletEntityByIdForUser(2, USER_ID)).thenReturn(toWallet);

            // when
            // then
            assertThatThrownBy(() -> transferService.createTransfer(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallets");

            verifyNoInteractions(walletBalanceService);
            verify(transferRepository, never()).save(any());
        }

        @Test
        void createTransfer_toFundWallet_rejected() {
            // given — regular transfer using a fund wallet as the destination must be blocked
            TransferCreateRequest request =
                    new TransferCreateRequest(1, 7, new BigDecimal("100.00"), DATE, null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(fromWallet);
            when(walletService.getWalletEntityByIdForUser(7, USER_ID)).thenReturn(fundWallet);

            // when
            // then
            assertThatThrownBy(() -> transferService.createTransfer(request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallets");

            verifyNoInteractions(walletBalanceService);
            verify(transferRepository, never()).save(any());
        }

        @Test
        void createTransfer_normalWallets_notBlocked() {
            // given — a normal transfer between two non-fund wallets must still succeed
            TransferCreateRequest request =
                    new TransferCreateRequest(1, 2, new BigDecimal("300.00"), DATE, null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(fromWallet);
            when(walletService.getWalletEntityByIdForUser(2, USER_ID)).thenReturn(toWallet);
            when(transferRepository.save(any(Transfer.class)))
                    .thenAnswer(inv -> {
                        Transfer t = inv.getArgument(0);
                        t.setId(10L);
                        return t;
                    });

            // when
            TransferResponse response = transferService.createTransfer(request, USER_ID);

            // then
            assertThat(response.fromWalletId()).isEqualTo(1);
            assertThat(response.toWalletId()).isEqualTo(2);
            verify(walletBalanceService).applyTransfer(eq(1), eq(2), any(), eq(USER_ID), anyLong(), eq(DATE));
        }
    }

    @Nested
    class FundTransferGuardOnMutation {

        @Test
        void updateTransfer_withFundWalletAsFrom_rejected() {
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fundWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(7, 2, new BigDecimal("500.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> transferService.updateTransfer(50L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallets");

            verify(transferRepository, never()).save(any());
            verifyNoInteractions(walletBalanceService);
        }

        @Test
        void updateTransfer_withFundWalletAsTo_rejected() {
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(fundWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(1, 7, new BigDecimal("500.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> transferService.updateTransfer(50L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallets");

            verify(transferRepository, never()).save(any());
            verifyNoInteractions(walletBalanceService);
        }

        @Test
        void updateTransfer_repointFromWalletToFund_rejected() {
            // existing transfer is between two normal wallets; request repoints source to a fund wallet
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(7, 2, new BigDecimal("500.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(walletService.getWalletEntityByIdForUser(7, USER_ID)).thenReturn(fundWallet);

            assertThatThrownBy(() -> transferService.updateTransfer(50L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallets");

            verify(transferRepository, never()).save(any());
            verifyNoInteractions(walletBalanceService);
        }

        @Test
        void updateTransfer_repointToWalletToFund_rejected() {
            // existing transfer is between two normal wallets; request repoints destination to a fund wallet
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(1, 7, new BigDecimal("500.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(walletService.getWalletEntityByIdForUser(7, USER_ID)).thenReturn(fundWallet);

            assertThatThrownBy(() -> transferService.updateTransfer(50L, request, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallets");

            verify(transferRepository, never()).save(any());
            verifyNoInteractions(walletBalanceService);
        }

        @Test
        void updateTransfer_repointToDifferentNormalWallet_succeeds() {
            // repointing the destination to another non-fund wallet must still pass the guard
            Wallet otherNormalWallet =
                    Wallet.builder()
                            .id(3)
                            .name("Holiday")
                            .balance(new BigDecimal("100.00"))
                            .user(user)
                            .build();

            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(1, 3, new BigDecimal("500.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(walletService.getWalletEntityByIdForUser(3, USER_ID)).thenReturn(otherNormalWallet);
            when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

            TransferResponse response = transferService.updateTransfer(50L, request, USER_ID);

            assertThat(response.toWalletId()).isEqualTo(3);
            verify(transferRepository).save(any(Transfer.class));
        }

        @Test
        void deleteTransfer_withFundWalletInvolved_rejected() {
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(fundWallet)
                            .amount(new BigDecimal("300.00"))
                            .transferDate(DATE)
                            .build();

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> transferService.deleteTransfer(50L, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fund wallets");

            verifyNoInteractions(walletBalanceService);
            verify(transferRepository, never()).delete(any());
        }

        @Test
        void updateTransfer_normalWallets_succeeds() {
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            TransferUpdateRequest request =
                    new TransferUpdateRequest(1, 2, new BigDecimal("750.00"), DATE, null);

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));
            when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

            TransferResponse response = transferService.updateTransfer(50L, request, USER_ID);

            assertThat(response.amount()).isEqualByComparingTo("750.00");
        }

        @Test
        void deleteTransfer_normalWallets_succeeds() {
            Transfer existing =
                    Transfer.builder()
                            .id(50L)
                            .fromWallet(fromWallet)
                            .toWallet(toWallet)
                            .amount(new BigDecimal("500.00"))
                            .transferDate(DATE)
                            .build();

            when(transferRepository.findByIdAndUserId(50L, USER_ID)).thenReturn(Optional.of(existing));

            transferService.deleteTransfer(50L, USER_ID);

            verify(walletBalanceService).reverseTransfer(
                    eq(1), eq(2), eq(new BigDecimal("500.00")), eq(USER_ID), eq(50L), eq(DATE));
            verify(transferRepository).delete(existing);
        }
    }

    @Nested
    class CreateFundTransfer {

        @Test
        void deposit_spendingToFund_succeeds() {
            // given — FundService depositing from a normal wallet into a fund wallet
            when(walletService.getWalletEntityByIdInternal(1)).thenReturn(fromWallet);
            when(walletService.getWalletEntityByIdInternal(7)).thenReturn(fundWallet);
            when(transferRepository.save(any(Transfer.class)))
                    .thenAnswer(inv -> {
                        Transfer t = inv.getArgument(0);
                        t.setId(99L);
                        return t;
                    });

            // when
            Transfer saved = transferService.createFundTransfer(
                    1, 7, new BigDecimal("500.00"), USER_ID, DATE, "Monthly savings");

            // then
            assertThat(saved.getId()).isEqualTo(99L);
            assertThat(saved.getFromWallet().getId()).isEqualTo(1);
            assertThat(saved.getToWallet().getId()).isEqualTo(7);
            assertThat(saved.getAmount()).isEqualByComparingTo("500.00");

            verify(transferRepository).save(any(Transfer.class));
            verify(walletBalanceService)
                    .applyTransfer(eq(1), eq(7), eq(new BigDecimal("500.00")), eq(USER_ID), eq(99L), eq(DATE));
        }

        @Test
        void withdrawal_fundToSpending_succeeds() {
            // given — FundService withdrawing from a fund wallet back to a normal wallet
            when(walletService.getWalletEntityByIdInternal(7)).thenReturn(fundWallet);
            when(walletService.getWalletEntityByIdInternal(1)).thenReturn(fromWallet);
            when(transferRepository.save(any(Transfer.class)))
                    .thenAnswer(inv -> {
                        Transfer t = inv.getArgument(0);
                        t.setId(100L);
                        return t;
                    });

            // when
            Transfer saved = transferService.createFundTransfer(
                    7, 1, new BigDecimal("200.00"), USER_ID, DATE, "Emergency");

            // then
            assertThat(saved.getFromWallet().getId()).isEqualTo(7);
            assertThat(saved.getToWallet().getId()).isEqualTo(1);
            assertThat(saved.getAmount()).isEqualByComparingTo("200.00");

            verify(walletBalanceService)
                    .applyTransfer(eq(7), eq(1), eq(new BigDecimal("200.00")), eq(USER_ID), eq(100L), eq(DATE));
        }

        @Test
        void selfTransfer_stillRejected() {
            // given — fund transfer to the same wallet must still be rejected
            // when
            // then
            assertThatThrownBy(
                    () -> transferService.createFundTransfer(
                            7, 7, new BigDecimal("100.00"), USER_ID, DATE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same wallet");

            verifyNoInteractions(walletBalanceService);
            verify(transferRepository, never()).save(any());
        }

        @Test
        void walletNotFound_throws() {
            // given
            when(walletService.getWalletEntityByIdInternal(7))
                    .thenThrow(new EntityNotFoundException("Wallet not found with id: 7"));

            // when
            // then
            assertThatThrownBy(
                    () -> transferService.createFundTransfer(
                            7, 1, new BigDecimal("100.00"), USER_ID, DATE, null))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(walletBalanceService);
        }

        @Test
        void persistsCorrectTransferFields() {
            // given
            when(walletService.getWalletEntityByIdInternal(1)).thenReturn(fromWallet);
            when(walletService.getWalletEntityByIdInternal(7)).thenReturn(fundWallet);

            ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
            when(transferRepository.save(captor.capture()))
                    .thenAnswer(inv -> {
                        Transfer t = inv.getArgument(0);
                        t.setId(55L);
                        return t;
                    });

            // when
            transferService.createFundTransfer(
                    1, 7, new BigDecimal("750.00"), USER_ID, DATE, "Quarterly top-up");

            // then
            Transfer captured = captor.getValue();
            assertThat(captured.getFromWallet()).isSameAs(fromWallet);
            assertThat(captured.getToWallet()).isSameAs(fundWallet);
            assertThat(captured.getAmount()).isEqualByComparingTo("750.00");
            assertThat(captured.getTransferDate()).isEqualTo(DATE);
            assertThat(captured.getNotes()).isEqualTo("Quarterly top-up");
        }
    }
}
