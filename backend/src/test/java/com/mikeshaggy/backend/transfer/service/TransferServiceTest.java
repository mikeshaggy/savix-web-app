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
}
