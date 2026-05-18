package com.mikeshaggy.backend.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.service.WalletEntryBalanceHistoryService;
import com.mikeshaggy.backend.ledger.service.WalletEntryService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletBalanceServiceTest {

    @Mock
    private WalletEntryService walletEntryService;

    @Mock
    private WalletService walletService;

    @Mock
    private WalletEntryBalanceHistoryService walletEntryBalanceHistoryService;

    @InjectMocks
    private WalletBalanceService walletBalanceService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    private Wallet wallet(int id, String balance) {
        Wallet w = new Wallet();
        w.setId(id);
        w.setBalance(new BigDecimal(balance));
        return w;
    }

    private void stubFindWallet(Wallet wallet) {
        when(walletService.getWalletEntityByIdForUser(wallet.getId(), USER_ID)).thenReturn(wallet);
    }

    @Nested
    class ResolveSignedAmount {

        @Test
        void income_returnsPositive() {
            // given
            // when
            BigDecimal result =
                    walletBalanceService.resolveSignedAmount(new BigDecimal("100"), CategoryType.INCOME);
            // then
            assertThat(result).isEqualByComparingTo("100");
        }

        @Test
        void expense_returnsNegative() {
            // given
            // when
            BigDecimal result =
                    walletBalanceService.resolveSignedAmount(new BigDecimal("100"), CategoryType.EXPENSE);
            // then
            assertThat(result).isEqualByComparingTo("-100");
        }
    }

    @Nested
    class ApplyTransaction {

        @Test
        void income_increasesBalance() {
            // given
            Wallet wallet = wallet(1, "500.00");
            stubFindWallet(wallet);

            // when
            walletBalanceService.applyTransaction(
                    1, new BigDecimal("200.00"), CategoryType.INCOME, USER_ID, 10L, DATE);

            // then
            verify(walletEntryService)
                    .createEntry(wallet, new BigDecimal("200.00"), DATE, SourceType.TRANSACTION, 10L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
        }

        @Test
        void expense_decreasesBalance() {
            // given
            Wallet wallet = wallet(1, "500.00");
            stubFindWallet(wallet);

            // when
            walletBalanceService.applyTransaction(
                    1, new BigDecimal("200.00"), CategoryType.EXPENSE, USER_ID, 10L, DATE);

            // then
            verify(walletEntryService)
                    .createEntry(wallet, new BigDecimal("-200.00"), DATE, SourceType.TRANSACTION, 10L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
        }

        @Test
        void walletNotFound_throws() {
            // given
            when(walletService.getWalletEntityByIdForUser(99, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found with id: 99"));

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    walletBalanceService.applyTransaction(
                                            99, new BigDecimal("100"), CategoryType.INCOME, USER_ID, 1L, DATE))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class ReverseTransaction {

        @Test
        void reverseIncome_decreasesBalance() {
            // given
            Wallet wallet = wallet(1, "700.00");
            stubFindWallet(wallet);

            // when
            walletBalanceService.reverseTransaction(
                    1, new BigDecimal("200.00"), CategoryType.INCOME, USER_ID, 10L, DATE);

            // then
            verify(walletEntryService)
                    .createEntry(wallet, new BigDecimal("-200.00"), DATE, SourceType.ADJUSTMENT, 10L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
        }

        @Test
        void reverseExpense_increasesBalance() {
            // given
            Wallet wallet = wallet(1, "300.00");
            stubFindWallet(wallet);

            // when
            walletBalanceService.reverseTransaction(
                    1, new BigDecimal("200.00"), CategoryType.EXPENSE, USER_ID, 10L, DATE);

            // then
            verify(walletEntryService)
                    .createEntry(wallet, new BigDecimal("200.00"), DATE, SourceType.ADJUSTMENT, 10L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
        }
    }

    @Nested
    class AdjustForTransactionEdit {

        @Test
        void sameWallet_amountChanged_appliesDelta() {
            // given
            Wallet wallet = wallet(1, "500.00");

            // when
            walletBalanceService.adjustForTransactionEdit(
                    wallet,
                    new BigDecimal("100.00"),
                    CategoryType.EXPENSE,
                    wallet,
                    new BigDecimal("150.00"),
                    CategoryType.EXPENSE,
                    10L,
                    DATE);

            // then
            verify(walletEntryService)
                    .createEntry(wallet, new BigDecimal("-50.00"), DATE, SourceType.ADJUSTMENT, 10L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
        }

        @Test
        void sameWallet_typeChanged_incomeToExpense() {
            // given
            Wallet wallet = wallet(1, "500.00");

            // when
            walletBalanceService.adjustForTransactionEdit(
                    wallet,
                    new BigDecimal("100.00"),
                    CategoryType.INCOME,
                    wallet,
                    new BigDecimal("100.00"),
                    CategoryType.EXPENSE,
                    10L,
                    DATE);

            // then
            verify(walletEntryService)
                    .createEntry(wallet, new BigDecimal("-200.00"), DATE, SourceType.ADJUSTMENT, 10L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
        }

        @Test
        void sameWallet_noChange_noAdjustment() {
            // given
            Wallet wallet = wallet(1, "500.00");

            // when
            walletBalanceService.adjustForTransactionEdit(
                    wallet,
                    new BigDecimal("100.00"),
                    CategoryType.EXPENSE,
                    wallet,
                    new BigDecimal("100.00"),
                    CategoryType.EXPENSE,
                    10L,
                    DATE);

            // then
            verifyNoInteractions(walletEntryService);
            verifyNoInteractions(walletEntryBalanceHistoryService);
        }

        @Test
        void walletChanged_revertsOldAndAppliesNew() {
            // given
            Wallet oldWallet = wallet(1, "500.00");
            Wallet newWallet = wallet(2, "1000.00");

            // when
            walletBalanceService.adjustForTransactionEdit(
                    oldWallet,
                    new BigDecimal("200.00"),
                    CategoryType.EXPENSE,
                    newWallet,
                    new BigDecimal("300.00"),
                    CategoryType.EXPENSE,
                    10L,
                    DATE);

            // then
            verify(walletEntryService)
                    .createEntry(oldWallet, new BigDecimal("200.00"), DATE, SourceType.ADJUSTMENT, 10L);
            verify(walletEntryService)
                    .createEntry(newWallet, new BigDecimal("-300.00"), DATE, SourceType.ADJUSTMENT, 10L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(2L);
        }

        @Test
        void walletChanged_withTypeAndAmountChange() {
            // given
            Wallet oldWallet = wallet(1, "500.00");
            Wallet newWallet = wallet(2, "1000.00");

            // when
            walletBalanceService.adjustForTransactionEdit(
                    oldWallet,
                    new BigDecimal("200.00"),
                    CategoryType.INCOME,
                    newWallet,
                    new BigDecimal("150.00"),
                    CategoryType.EXPENSE,
                    10L,
                    DATE);

            // then
            verify(walletEntryService)
                    .createEntry(oldWallet, new BigDecimal("-200.00"), DATE, SourceType.ADJUSTMENT, 10L);
            verify(walletEntryService)
                    .createEntry(newWallet, new BigDecimal("-150.00"), DATE, SourceType.ADJUSTMENT, 10L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(2L);
        }
    }

    @Nested
    class ApplyTransfer {

        @Test
        void transfer_decreasesFromAndIncreasesTo() {
            // given
            Wallet from = wallet(1, "1000.00");
            Wallet to = wallet(2, "500.00");
            stubFindWallet(from);
            stubFindWallet(to);

            // when
            walletBalanceService.applyTransfer(1, 2, new BigDecimal("250.00"), USER_ID, 5L, DATE);

            // then
            verify(walletEntryService)
                    .createEntry(from, new BigDecimal("-250.00"), DATE, SourceType.TRANSFER, 5L);
            verify(walletEntryService)
                    .createEntry(to, new BigDecimal("250.00"), DATE, SourceType.TRANSFER, 5L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(2L);
        }
    }

    @Nested
    class ReverseTransfer {

        @Test
        void reversal_restoresBalances() {
            // given
            Wallet from = wallet(1, "750.00");
            Wallet to = wallet(2, "750.00");
            stubFindWallet(from);
            stubFindWallet(to);

            // when
            walletBalanceService.reverseTransfer(1, 2, new BigDecimal("250.00"), USER_ID, 5L, DATE);

            // then
            verify(walletEntryService)
                    .createEntry(from, new BigDecimal("250.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryService)
                    .createEntry(to, new BigDecimal("-250.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(2L);
        }
    }

    @Nested
    class AdjustForTransferEdit {

        @Test
        void amountOnlyChanged_adjustsBothWallets() {
            // given
            Wallet from = wallet(1, "750.00");
            Wallet to = wallet(2, "750.00");

            // when
            walletBalanceService.adjustForTransferEdit(
                    from, to, new BigDecimal("250.00"), from, to, new BigDecimal("300.00"), 5L, DATE);

            // then
            verify(walletEntryService)
                    .createEntry(from, new BigDecimal("-50.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryService)
                    .createEntry(to, new BigDecimal("50.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(2L);
        }

        @Test
        void fromWalletChanged_revertsOldFromAndAppliesNewFrom() {
            // given
            Wallet oldFrom = wallet(1, "750.00");
            Wallet oldTo = wallet(2, "750.00");
            Wallet newFrom = wallet(3, "2000.00");

            // when
            walletBalanceService.adjustForTransferEdit(
                    oldFrom,
                    oldTo,
                    new BigDecimal("250.00"),
                    newFrom,
                    oldTo,
                    new BigDecimal("300.00"),
                    5L,
                    DATE);

            // then
            verify(walletEntryService)
                    .createEntry(oldFrom, new BigDecimal("250.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryService)
                    .createEntry(newFrom, new BigDecimal("-300.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryService)
                    .createEntry(oldTo, new BigDecimal("50.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(2L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(3L);
        }

        @Test
        void toWalletChanged_revertsOldToAndAppliesNewTo() {
            // given
            Wallet oldFrom = wallet(1, "750.00");
            Wallet oldTo = wallet(2, "750.00");
            Wallet newTo = wallet(3, "100.00");

            // when
            walletBalanceService.adjustForTransferEdit(
                    oldFrom,
                    oldTo,
                    new BigDecimal("250.00"),
                    oldFrom,
                    newTo,
                    new BigDecimal("300.00"),
                    5L,
                    DATE);

            // then
            verify(walletEntryService)
                    .createEntry(oldFrom, new BigDecimal("-50.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryService)
                    .createEntry(oldTo, new BigDecimal("-250.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryService)
                    .createEntry(newTo, new BigDecimal("300.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(2L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(3L);
        }

        @Test
        void bothWalletsChanged() {
            // given
            Wallet oldFrom = wallet(1, "750.00");
            Wallet oldTo = wallet(2, "750.00");
            Wallet newFrom = wallet(3, "2000.00");
            Wallet newTo = wallet(4, "100.00");

            // when
            walletBalanceService.adjustForTransferEdit(
                    oldFrom,
                    oldTo,
                    new BigDecimal("250.00"),
                    newFrom,
                    newTo,
                    new BigDecimal("300.00"),
                    5L,
                    DATE);

            // then
            verify(walletEntryService)
                    .createEntry(oldFrom, new BigDecimal("250.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryService)
                    .createEntry(newFrom, new BigDecimal("-300.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryService)
                    .createEntry(oldTo, new BigDecimal("-250.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryService)
                    .createEntry(newTo, new BigDecimal("300.00"), DATE, SourceType.ADJUSTMENT, 5L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(1L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(2L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(3L);
            verify(walletEntryBalanceHistoryService).recalculateWalletLedger(4L);
        }

        @Test
        void noChange_noAdjustment() {
            // given
            Wallet from = wallet(1, "750.00");
            Wallet to = wallet(2, "750.00");

            // when
            walletBalanceService.adjustForTransferEdit(
                    from, to, new BigDecimal("250.00"), from, to, new BigDecimal("250.00"), 5L, DATE);

            // then
            verifyNoInteractions(walletEntryService);
            verifyNoInteractions(walletEntryBalanceHistoryService);
        }
    }
}
