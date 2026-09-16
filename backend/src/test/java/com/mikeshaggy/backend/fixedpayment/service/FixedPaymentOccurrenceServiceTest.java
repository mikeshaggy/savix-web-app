package com.mikeshaggy.backend.fixedpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.common.exception.ConflictException;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class FixedPaymentOccurrenceServiceTest {

    @Mock
    private FixedPaymentOccurrenceRepository occurrenceRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private FixedPaymentOccurrenceService fixedPaymentOccurrenceService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);

    private User user;
    private Wallet wallet;
    private Category category;

    @BeforeEach
    void setUp() {
        Instant fixedInstant = TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant();
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
        wallet =
                Wallet.builder().id(1).name("Main").balance(new BigDecimal("5000.00")).user(user).build();
        category =
                Category.builder()
                        .id(10)
                        .name("Rent")
                        .type(CategoryType.EXPENSE)
                        .emoji("🏠")
                        .user(user)
                        .build();
    }

    private FixedPayment buildFixedPayment(Integer id) {
        return FixedPayment.builder()
                .id(id)
                .wallet(wallet)
                .category(category)
                .title("Monthly Rent")
                .amount(new BigDecimal("1500.00"))
                .anchorDate(LocalDate.of(2026, 1, 1))
                .cycle(Cycle.MONTHLY)
                .activeFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    @Nested
    class MarkOccurrenceAsPaid {

        @Test
        void happyPath_setsStatusAmountTimestampAndTransaction() {
            // given
            FixedPayment fp = buildFixedPayment(1);
            FixedPaymentOccurrence occurrence =
                    FixedPaymentOccurrence.builder()
                            .id(100L)
                            .fixedPayment(fp)
                            .dueDate(LocalDate.of(2026, 3, 1))
                            .expectedAmount(new BigDecimal("1500.00"))
                            .status(OccurrenceStatus.PENDING)
                            .build();

            Transaction savedTransaction =
                    Transaction.builder().id(200L).amount(new BigDecimal("1500.00")).transactionDate(TODAY).build();

            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(100L, USER_ID))
                    .thenReturn(Optional.of(occurrence));

            // when
            fixedPaymentOccurrenceService.markOccurrenceAsPaid(100L, savedTransaction, USER_ID);

            ArgumentCaptor<FixedPaymentOccurrence> captor =
                    ArgumentCaptor.forClass(FixedPaymentOccurrence.class);
            // then
            verify(occurrenceRepository).save(captor.capture());
            FixedPaymentOccurrence saved = captor.getValue();

            assertThat(saved.getStatus()).isEqualTo(OccurrenceStatus.PAID);
            assertThat(saved.getPaidAmount()).isEqualByComparingTo("1500.00");
            assertThat(saved.getPaidAt()).isEqualTo(TODAY.atStartOfDay());
            assertThat(saved.getTransaction()).isSameAs(savedTransaction);
        }

        @Test
        void overdueOccurrence_canStillBeMarkedAsPaid() {
            // given
            FixedPayment fp = buildFixedPayment(1);
            FixedPaymentOccurrence occurrence =
                    FixedPaymentOccurrence.builder()
                            .id(101L)
                            .fixedPayment(fp)
                            .dueDate(LocalDate.of(2026, 2, 1))
                            .expectedAmount(new BigDecimal("1500.00"))
                            .status(OccurrenceStatus.OVERDUE)
                            .build();

            Transaction savedTransaction =
                    Transaction.builder().id(201L).amount(new BigDecimal("1500.00")).transactionDate(TODAY).build();

            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(101L, USER_ID))
                    .thenReturn(Optional.of(occurrence));

            // when
            fixedPaymentOccurrenceService.markOccurrenceAsPaid(101L, savedTransaction, USER_ID);

            // then
            assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.PAID);
            verify(occurrenceRepository).save(occurrence);
        }

        @Test
        void paidAmountCanDifferFromExpected() {
            // given
            FixedPayment fp = buildFixedPayment(1);
            FixedPaymentOccurrence occurrence =
                    FixedPaymentOccurrence.builder()
                            .id(102L)
                            .fixedPayment(fp)
                            .dueDate(LocalDate.of(2026, 3, 1))
                            .expectedAmount(new BigDecimal("1500.00"))
                            .status(OccurrenceStatus.PENDING)
                            .build();

            Transaction savedTransaction =
                    Transaction.builder().id(202L).amount(new BigDecimal("1450.00")).transactionDate(TODAY).build();

            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(102L, USER_ID))
                    .thenReturn(Optional.of(occurrence));

            // when
            fixedPaymentOccurrenceService.markOccurrenceAsPaid(102L, savedTransaction, USER_ID);

            // then
            assertThat(occurrence.getPaidAmount()).isEqualByComparingTo("1450.00");
        }

        @Test
        void crossUserOccurrence_behavesLikeNotFoundAndDoesNotMutateState() {
            // given
            User otherUser = User.builder().id(UUID.randomUUID()).build();
            Wallet otherWallet = Wallet.builder().id(2).user(otherUser).build();
            FixedPayment otherFp =
                    FixedPayment.builder()
                            .id(5)
                            .wallet(otherWallet)
                            .category(category)
                            .title("Other")
                            .amount(new BigDecimal("100.00"))
                            .anchorDate(TODAY)
                            .cycle(Cycle.MONTHLY)
                            .activeFrom(TODAY)
                            .build();

            FixedPaymentOccurrence occurrence =
                    FixedPaymentOccurrence.builder()
                            .id(103L)
                            .fixedPayment(otherFp)
                            .status(OccurrenceStatus.PENDING)
                            .build();

            Transaction tx = Transaction.builder().id(203L).amount(new BigDecimal("100.00")).build();

            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(103L, USER_ID))
                    .thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(
                            () -> fixedPaymentOccurrenceService.markOccurrenceAsPaid(103L, tx, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Occurrence not found");

            assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.PENDING);
            assertThat(occurrence.getPaidAmount()).isNull();
            assertThat(occurrence.getPaidAt()).isNull();
            assertThat(occurrence.getTransaction()).isNull();
            verify(occurrenceRepository, never()).save(any());
        }

        @Test
        void occurrenceNotFound_throwsEntityNotFound() {
            // given
            Transaction tx = Transaction.builder().id(204L).amount(new BigDecimal("100.00")).build();

            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(999L, USER_ID))
                    .thenReturn(Optional.empty());

            // when
            // then
            assertThatThrownBy(
                            () -> fixedPaymentOccurrenceService.markOccurrenceAsPaid(999L, tx, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Occurrence not found");
        }
    }

    private FixedPaymentOccurrence buildOccurrence(Long id, OccurrenceStatus status, LocalDate dueDate) {
        return FixedPaymentOccurrence.builder()
                .id(id)
                .fixedPayment(buildFixedPayment(1))
                .dueDate(dueDate)
                .expectedAmount(new BigDecimal("1500.00"))
                .status(status)
                .build();
    }

    private Transaction buildExpenseTransaction(Long id, Wallet txWallet) {
        return Transaction.builder()
                .id(id)
                .wallet(txWallet)
                .category(category)
                .amount(new BigDecimal("1480.00"))
                .transactionDate(TODAY)
                .build();
    }

    @Nested
    class LinkExistingTransaction {

        @Test
        void happyPath_marksPaidAndLinksBothSides() {
            // given
            FixedPaymentOccurrence occurrence =
                    buildOccurrence(300L, OccurrenceStatus.PENDING, LocalDate.of(2026, 3, 1));
            Transaction tx = buildExpenseTransaction(400L, wallet);
            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(300L, USER_ID))
                    .thenReturn(Optional.of(occurrence));
            when(transactionRepository.findByIdAndWalletUserId(400L, USER_ID))
                    .thenReturn(Optional.of(tx));

            // when
            FixedOccurrenceRowDto dto =
                    fixedPaymentOccurrenceService.linkExistingTransaction(300L, 400L, USER_ID);

            // then
            assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.PAID);
            assertThat(occurrence.getPaidAmount()).isEqualByComparingTo("1480.00");
            assertThat(occurrence.getPaidAt()).isNotNull();
            assertThat(occurrence.getTransaction()).isSameAs(tx);
            assertThat(tx.getFixedPaymentOccurrence()).isSameAs(occurrence);
            assertThat(dto.transactionId()).isEqualTo(400L);
            assertThat(dto.status()).isEqualTo(OccurrenceStatus.PAID);
            verify(occurrenceRepository).save(occurrence);
        }

        @Test
        void alreadyPaidOccurrence_isRejected() {
            // given
            FixedPaymentOccurrence occurrence =
                    buildOccurrence(301L, OccurrenceStatus.PAID, LocalDate.of(2026, 3, 1));
            occurrence.setTransaction(Transaction.builder().id(999L).build());
            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(301L, USER_ID))
                    .thenReturn(Optional.of(occurrence));

            // when / then
            assertThatThrownBy(
                            () -> fixedPaymentOccurrenceService.linkExistingTransaction(301L, 400L, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already linked");
            verify(occurrenceRepository, never()).save(any());
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void transactionAlreadyLinkedElsewhere_isRejected() {
            // given
            FixedPaymentOccurrence occurrence =
                    buildOccurrence(302L, OccurrenceStatus.PENDING, LocalDate.of(2026, 3, 1));
            Transaction tx = buildExpenseTransaction(401L, wallet);
            tx.setFixedPaymentOccurrence(buildOccurrence(999L, OccurrenceStatus.PAID, LocalDate.of(2026, 1, 1)));
            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(302L, USER_ID))
                    .thenReturn(Optional.of(occurrence));
            when(transactionRepository.findByIdAndWalletUserId(401L, USER_ID))
                    .thenReturn(Optional.of(tx));

            // when / then
            assertThatThrownBy(
                            () -> fixedPaymentOccurrenceService.linkExistingTransaction(302L, 401L, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already linked");
            verify(occurrenceRepository, never()).save(any());
        }

        @Test
        void incomeTransaction_isRejected() {
            // given
            FixedPaymentOccurrence occurrence =
                    buildOccurrence(303L, OccurrenceStatus.PENDING, LocalDate.of(2026, 3, 1));
            Category incomeCategory =
                    Category.builder().id(20).name("Salary").type(CategoryType.INCOME).user(user).build();
            Transaction tx =
                    Transaction.builder().id(402L).wallet(wallet).category(incomeCategory)
                            .amount(new BigDecimal("1480.00")).build();
            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(303L, USER_ID))
                    .thenReturn(Optional.of(occurrence));
            when(transactionRepository.findByIdAndWalletUserId(402L, USER_ID))
                    .thenReturn(Optional.of(tx));

            // when / then
            assertThatThrownBy(
                            () -> fixedPaymentOccurrenceService.linkExistingTransaction(303L, 402L, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expense");
            verify(occurrenceRepository, never()).save(any());
        }

        @Test
        void transactionInDifferentWallet_isRejected() {
            // given
            FixedPaymentOccurrence occurrence =
                    buildOccurrence(304L, OccurrenceStatus.PENDING, LocalDate.of(2026, 3, 1));
            Wallet otherWallet = Wallet.builder().id(2).name("Other").user(user).build();
            Transaction tx = buildExpenseTransaction(403L, otherWallet);
            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(304L, USER_ID))
                    .thenReturn(Optional.of(occurrence));
            when(transactionRepository.findByIdAndWalletUserId(403L, USER_ID))
                    .thenReturn(Optional.of(tx));

            // when / then
            assertThatThrownBy(
                            () -> fixedPaymentOccurrenceService.linkExistingTransaction(304L, 403L, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("wallet");
            verify(occurrenceRepository, never()).save(any());
        }
    }

    @Nested
    class UnlinkByOccurrenceId {

        @Test
        void linkedOccurrenceBeforeDueDate_resetsToPending() {
            // given (due date in the future relative to TODAY 2026-03-11)
            FixedPaymentOccurrence occurrence =
                    buildOccurrence(500L, OccurrenceStatus.PAID, LocalDate.of(2026, 4, 1));
            Transaction tx = buildExpenseTransaction(600L, wallet);
            occurrence.setTransaction(tx);
            tx.setFixedPaymentOccurrence(occurrence);
            occurrence.setPaidAmount(new BigDecimal("1480.00"));
            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(500L, USER_ID))
                    .thenReturn(Optional.of(occurrence));

            // when
            FixedOccurrenceRowDto dto =
                    fixedPaymentOccurrenceService.unlinkByOccurrenceId(500L, USER_ID);

            // then
            assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.PENDING);
            assertThat(occurrence.getTransaction()).isNull();
            assertThat(occurrence.getPaidAmount()).isNull();
            assertThat(occurrence.getPaidAt()).isNull();
            assertThat(tx.getFixedPaymentOccurrence()).isNull();
            assertThat(dto.transactionId()).isNull();
            verify(occurrenceRepository).save(occurrence);
        }

        @Test
        void linkedOccurrencePastDueDate_resetsToOverdue() {
            // given (due date before TODAY 2026-03-11)
            FixedPaymentOccurrence occurrence =
                    buildOccurrence(501L, OccurrenceStatus.PAID, LocalDate.of(2026, 2, 1));
            Transaction tx = buildExpenseTransaction(601L, wallet);
            occurrence.setTransaction(tx);
            tx.setFixedPaymentOccurrence(occurrence);
            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(501L, USER_ID))
                    .thenReturn(Optional.of(occurrence));

            // when
            fixedPaymentOccurrenceService.unlinkByOccurrenceId(501L, USER_ID);

            // then
            assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.OVERDUE);
        }

        @Test
        void unlinkedOccurrence_isRejected() {
            // given
            FixedPaymentOccurrence occurrence =
                    buildOccurrence(502L, OccurrenceStatus.PENDING, LocalDate.of(2026, 3, 1));
            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(502L, USER_ID))
                    .thenReturn(Optional.of(occurrence));

            // when / then
            assertThatThrownBy(
                            () -> fixedPaymentOccurrenceService.unlinkByOccurrenceId(502L, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("not linked");
            verify(occurrenceRepository, never()).save(any());
        }
    }

    /** Stage 3.2 — paid date and lateness derive from the transaction date, never from the link time. */
    @Nested
    class PaidDateAndLateness {

        private static final LocalDate DUE = LocalDate.of(2026, 9, 10);

        private Transaction expenseDated(Long id, LocalDate date) {
            return Transaction.builder()
                    .id(id)
                    .wallet(wallet)
                    .category(category)
                    .amount(new BigDecimal("1879.91"))
                    .transactionDate(date)
                    .build();
        }

        private FixedOccurrenceRowDto link(Long occurrenceId, Transaction tx) {
            FixedPaymentOccurrence occurrence = buildOccurrence(occurrenceId, OccurrenceStatus.PENDING, DUE);
            when(occurrenceRepository.findByIdAndFixedPaymentWalletUserId(occurrenceId, USER_ID))
                    .thenReturn(Optional.of(occurrence));
            when(transactionRepository.findByIdAndWalletUserId(tx.getId(), USER_ID))
                    .thenReturn(Optional.of(tx));
            return fixedPaymentOccurrenceService.linkExistingTransaction(occurrenceId, tx.getId(), USER_ID);
        }

        @Test
        void transactionDatedDayAfterDueDate_isOneDayLate() {
            FixedOccurrenceRowDto dto = link(500L, expenseDated(600L, DUE.plusDays(1)));

            assertThat(dto.paidDate()).isEqualTo(LocalDate.of(2026, 9, 11));
            assertThat(dto.lateDays()).isEqualTo(1);
            assertThat(dto.paidOnTime()).isFalse();
            assertThat(dto.paidAt()).isEqualTo(LocalDate.of(2026, 9, 11).atStartOfDay());
        }

        @Test
        void transactionDatedOnDueDate_isOnTime() {
            FixedOccurrenceRowDto dto = link(501L, expenseDated(601L, DUE));

            assertThat(dto.paidDate()).isEqualTo(DUE);
            assertThat(dto.lateDays()).isZero();
            assertThat(dto.paidOnTime()).isTrue();
        }

        @Test
        void transactionDatedBeforeDueDate_isOnTimeWithNegativeLateDays() {
            FixedOccurrenceRowDto dto = link(502L, expenseDated(602L, DUE.minusDays(3)));

            assertThat(dto.lateDays()).isEqualTo(-3);
            assertThat(dto.paidOnTime()).isTrue();
        }

        @Test
        void linkingAtHalfPastMidnightTheNextDay_doesNotChangeTheVerdict() {
            // the clock (link time) is Sep 11 00:30 UTC; the transaction is dated Sep 10
            Instant linkInstant = DUE.plusDays(1).atTime(0, 30).atZone(ZoneId.of("UTC")).toInstant();
            when(clock.instant()).thenReturn(linkInstant);
            when(clock.getZone()).thenReturn(ZoneId.of("UTC"));

            FixedOccurrenceRowDto dto = link(503L, expenseDated(603L, DUE));

            assertThat(dto.paidDate()).isEqualTo(DUE);
            assertThat(dto.lateDays()).isZero();
            assertThat(dto.paidOnTime()).isTrue();
            assertThat(dto.paidAt()).isEqualTo(DUE.atStartOfDay());
        }

        @Test
        void paidAmountIsTheTransactionAmount_expectedAmountStaysPlanned() {
            FixedOccurrenceRowDto dto = link(504L, expenseDated(604L, DUE));

            assertThat(dto.paidAmount()).isEqualByComparingTo("1879.91");
            assertThat(dto.expectedAmount()).isEqualByComparingTo("1500.00");
        }

        @Test
        void unpaidRow_hasNoPaidDateLatenessOrVerdict() {
            FixedPaymentOccurrence occurrence = buildOccurrence(505L, OccurrenceStatus.PENDING, DUE);

            FixedOccurrenceRowDto dto = FixedOccurrenceRowDto.from(occurrence, TODAY);

            assertThat(dto.paidDate()).isNull();
            assertThat(dto.lateDays()).isNull();
            assertThat(dto.paidOnTime()).isNull();
        }

        @Test
        void legacyPaidRowWithoutTransaction_fallsBackToPaidAtDate() {
            FixedPaymentOccurrence occurrence = buildOccurrence(506L, OccurrenceStatus.PAID, DUE);
            occurrence.setPaidAt(DUE.plusDays(2).atTime(23, 45)); // historical link timestamp

            FixedOccurrenceRowDto dto = FixedOccurrenceRowDto.from(occurrence, TODAY);

            assertThat(dto.paidDate()).isEqualTo(DUE.plusDays(2));
            assertThat(dto.lateDays()).isEqualTo(2);
            assertThat(dto.paidOnTime()).isFalse();
        }

        @Test
        void linkedTransactionWinsOverStalePaidAt() {
            // a row linked before Stage 3.2 keeps the link timestamp in paidAt; the transaction date rules
            FixedPaymentOccurrence occurrence = buildOccurrence(507L, OccurrenceStatus.PAID, DUE);
            occurrence.setPaidAt(DUE.plusDays(5).atTime(9, 0));
            occurrence.setTransaction(expenseDated(607L, DUE));

            FixedOccurrenceRowDto dto = FixedOccurrenceRowDto.from(occurrence, TODAY);

            assertThat(dto.paidDate()).isEqualTo(DUE);
            assertThat(dto.lateDays()).isZero();
            assertThat(dto.paidOnTime()).isTrue();
        }

        @Test
        void syncWithTransaction_updatesPaidAmountAndPaidDate() {
            FixedPaymentOccurrence occurrence = buildOccurrence(508L, OccurrenceStatus.PAID, DUE);
            occurrence.setPaidAmount(new BigDecimal("1480.00"));
            occurrence.setPaidAt(DUE.atStartOfDay());

            fixedPaymentOccurrenceService.syncWithTransaction(
                    occurrence, new BigDecimal("1600.00"), DUE.plusDays(4));

            assertThat(occurrence.getPaidAmount()).isEqualByComparingTo("1600.00");
            assertThat(occurrence.getPaidAt()).isEqualTo(DUE.plusDays(4).atStartOfDay());
            verify(occurrenceRepository).save(occurrence);
        }
    }
}
