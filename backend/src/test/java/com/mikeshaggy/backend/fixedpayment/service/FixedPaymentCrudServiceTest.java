package com.mikeshaggy.backend.fixedpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.dto.CreateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.dto.FixedPaymentResponse;
import com.mikeshaggy.backend.fixedpayment.dto.UpdateFixedPaymentRequest;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class FixedPaymentCrudServiceTest {

    @Mock
    private FixedPaymentRepository fixedPaymentRepository;

    @Mock
    private FixedPaymentOccurrenceRepository occurrenceRepository;

    @Mock
    private FixedPaymentOccurrenceGenerationService generationService;

    @Mock
    private WalletService walletService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private Clock clock;

    @InjectMocks
    private FixedPaymentCrudService fixedPaymentCrudService;

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
    class CreateFixedPayment {

        @Test
        void happyPath_persistsCorrectDataAndTriggersGeneration() {
            // given
            CreateFixedPaymentRequest request =
                    new CreateFixedPaymentRequest(
                            1,
                            10,
                            "Monthly Rent",
                            new BigDecimal("1500.00"),
                            LocalDate.of(2026, 1, 1),
                            Cycle.MONTHLY,
                            LocalDate.of(2026, 1, 1),
                            null,
                            "Apartment rent");

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(10, USER_ID)).thenReturn(category);
            when(fixedPaymentRepository.save(any(FixedPayment.class)))
                    .thenAnswer(
                            inv -> {
                                FixedPayment fp = inv.getArgument(0);
                                fp.setId(100);
                                return fp;
                            });

            // when
            FixedPaymentResponse response = fixedPaymentCrudService.createFixedPayment(request, USER_ID);

            // then
            assertThat(response.id()).isEqualTo(100);
            assertThat(response.title()).isEqualTo("Monthly Rent");
            assertThat(response.amount()).isEqualByComparingTo("1500.00");
            assertThat(response.cycle()).isEqualTo(Cycle.MONTHLY);
            assertThat(response.walletId()).isEqualTo(1);
            assertThat(response.categoryId()).isEqualTo(10);
            assertThat(response.activeFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(response.notes()).isEqualTo("Apartment rent");

            ArgumentCaptor<FixedPayment> captor = ArgumentCaptor.forClass(FixedPayment.class);
            verify(fixedPaymentRepository).save(captor.capture());
            FixedPayment saved = captor.getValue();
            assertThat(saved.getWallet()).isSameAs(wallet);
            assertThat(saved.getCategory()).isSameAs(category);
            assertThat(saved.getAnchorDate()).isEqualTo(LocalDate.of(2026, 1, 1));

            verify(generationService).ensureOccurrencesGenerated(USER_ID);
        }

        @Test
        void nullActiveFrom_defaultsToToday() {
            // given
            CreateFixedPaymentRequest request =
                    new CreateFixedPaymentRequest(
                            1,
                            10,
                            "Insurance",
                            new BigDecimal("200.00"),
                            LocalDate.of(2026, 6, 15),
                            Cycle.YEARLY,
                            null,
                            null,
                            null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(10, USER_ID)).thenReturn(category);
            when(fixedPaymentRepository.save(any(FixedPayment.class)))
                    .thenAnswer(
                            inv -> {
                                FixedPayment fp = inv.getArgument(0);
                                fp.setId(101);
                                return fp;
                            });

            // when
            FixedPaymentResponse response = fixedPaymentCrudService.createFixedPayment(request, USER_ID);

            // then
            assertThat(response.activeFrom()).isEqualTo(TODAY);
        }

        @Test
        void walletNotFound_throwsEntityNotFound() {
            // given
            CreateFixedPaymentRequest request =
                    new CreateFixedPaymentRequest(
                            999,
                            10,
                            "Rent",
                            new BigDecimal("1500.00"),
                            LocalDate.of(2026, 1, 1),
                            Cycle.MONTHLY,
                            null,
                            null,
                            null);

            when(walletService.getWalletEntityByIdForUser(999, USER_ID))
                    .thenThrow(new EntityNotFoundException("Wallet not found with id: 999"));

            // when
            // then
            assertThatThrownBy(() -> fixedPaymentCrudService.createFixedPayment(request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Wallet not found");

            verify(fixedPaymentRepository, never()).save(any());
            verify(generationService, never()).ensureOccurrencesGenerated(any());
        }

        @Test
        void categoryNotFound_throwsEntityNotFound() {
            // given
            CreateFixedPaymentRequest request =
                    new CreateFixedPaymentRequest(
                            1,
                            999,
                            "Rent",
                            new BigDecimal("1500.00"),
                            LocalDate.of(2026, 1, 1),
                            Cycle.MONTHLY,
                            null,
                            null,
                            null);

            when(walletService.getWalletEntityByIdForUser(1, USER_ID)).thenReturn(wallet);
            when(categoryService.getCategoryEntityByIdForUser(999, USER_ID))
                    .thenThrow(new EntityNotFoundException("Category not found with id: 999"));

            // when
            // then
            assertThatThrownBy(() -> fixedPaymentCrudService.createFixedPayment(request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Category not found");

            verify(fixedPaymentRepository, never()).save(any());
        }
    }

    @Nested
    class UpdateFixedPayment {

        @Test
        void noStructuralChange_updatesFieldsWithoutPurgingOccurrences() {
            // given
            FixedPayment existing = buildFixedPayment(1);
            UpdateFixedPaymentRequest request =
                    new UpdateFixedPaymentRequest(
                            "Updated Rent",
                            existing.getAmount(),
                            existing.getAnchorDate(),
                            existing.getCycle(),
                            "updated notes",
                            LocalDate.of(2027, 12, 31));

            when(fixedPaymentRepository.findByIdAndWalletUserId(1, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(fixedPaymentRepository.save(any(FixedPayment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            FixedPaymentResponse response =
                    fixedPaymentCrudService.updateFixedPayment(1, request, USER_ID);

            // then
            assertThat(response.title()).isEqualTo("Updated Rent");
            assertThat(response.notes()).isEqualTo("updated notes");
            assertThat(response.activeTo()).isEqualTo(LocalDate.of(2027, 12, 31));

            verify(occurrenceRepository, never()).findFuturePendingByFixedPaymentId(anyInt(), any());
            verify(occurrenceRepository, never()).deleteAll(anyList());
            verify(generationService, never()).ensureOccurrencesGenerated(any());
        }

        @Test
        void amountChanged_purgesFuturePendingAndRegenerates() {
            // given
            FixedPayment existing = buildFixedPayment(1);
            UpdateFixedPaymentRequest request =
                    new UpdateFixedPaymentRequest(
                            "Rent",
                            new BigDecimal("1600.00"),
                            existing.getAnchorDate(),
                            existing.getCycle(),
                            null,
                            null);

            FixedPaymentOccurrence futureOcc =
                    FixedPaymentOccurrence.builder()
                            .id(50L)
                            .fixedPayment(existing)
                            .status(OccurrenceStatus.PENDING)
                            .build();

            when(fixedPaymentRepository.findByIdAndWalletUserId(1, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(occurrenceRepository.findFuturePendingByFixedPaymentId(eq(1), any(LocalDate.class)))
                    .thenReturn(List.of(futureOcc));
            when(fixedPaymentRepository.save(any(FixedPayment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            FixedPaymentResponse response =
                    fixedPaymentCrudService.updateFixedPayment(1, request, USER_ID);

            // then
            assertThat(response.amount()).isEqualByComparingTo("1600.00");
            verify(occurrenceRepository).deleteAll(List.of(futureOcc));
            verify(generationService).ensureOccurrencesGenerated(USER_ID);
        }

        @Test
        void cycleChanged_purgesFuturePendingAndRegenerates() {
            // given
            FixedPayment existing = buildFixedPayment(1);
            UpdateFixedPaymentRequest request =
                    new UpdateFixedPaymentRequest(
                            "Rent", existing.getAmount(), existing.getAnchorDate(), Cycle.QUARTERLY, null, null);

            when(fixedPaymentRepository.findByIdAndWalletUserId(1, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(occurrenceRepository.findFuturePendingByFixedPaymentId(eq(1), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(fixedPaymentRepository.save(any(FixedPayment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            FixedPaymentResponse response =
                    fixedPaymentCrudService.updateFixedPayment(1, request, USER_ID);

            // then
            assertThat(response.cycle()).isEqualTo(Cycle.QUARTERLY);
            verify(occurrenceRepository).deleteAll(List.of());
            verify(generationService).ensureOccurrencesGenerated(USER_ID);
        }

        @Test
        void anchorDateChanged_purgesFuturePendingAndRegenerates() {
            // given
            FixedPayment existing = buildFixedPayment(1);
            UpdateFixedPaymentRequest request =
                    new UpdateFixedPaymentRequest(
                            "Rent",
                            existing.getAmount(),
                            LocalDate.of(2026, 2, 15),
                            existing.getCycle(),
                            null,
                            null);

            when(fixedPaymentRepository.findByIdAndWalletUserId(1, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(occurrenceRepository.findFuturePendingByFixedPaymentId(eq(1), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(fixedPaymentRepository.save(any(FixedPayment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            fixedPaymentCrudService.updateFixedPayment(1, request, USER_ID);

            // then
            verify(occurrenceRepository).deleteAll(anyList());
            verify(generationService).ensureOccurrencesGenerated(USER_ID);
        }

        @Test
        void fixedPaymentNotFound_throwsEntityNotFound() {
            // given
            UpdateFixedPaymentRequest request =
                    new UpdateFixedPaymentRequest(
                            "Rent",
                            new BigDecimal("1500.00"),
                            LocalDate.of(2026, 1, 1),
                            Cycle.MONTHLY,
                            null,
                            null);

            when(fixedPaymentRepository.findByIdAndWalletUserId(999, USER_ID))
                    .thenReturn(Optional.empty());

            // when / then
            // when
            // then
            assertThatThrownBy(() -> fixedPaymentCrudService.updateFixedPayment(999, request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Fixed payment not found");
        }
    }

    @Nested
    class DeactivateFixedPayment {

        @Test
        void setsActiveToTodayAndPurgesFuturePending() {
            // given
            FixedPayment existing = buildFixedPayment(1);
            assertThat(existing.getActiveTo()).isNull();

            FixedPaymentOccurrence futureOcc1 =
                    FixedPaymentOccurrence.builder()
                            .id(60L)
                            .fixedPayment(existing)
                            .status(OccurrenceStatus.PENDING)
                            .build();
            FixedPaymentOccurrence futureOcc2 =
                    FixedPaymentOccurrence.builder()
                            .id(61L)
                            .fixedPayment(existing)
                            .status(OccurrenceStatus.PENDING)
                            .build();

            when(fixedPaymentRepository.findByIdAndWalletUserId(1, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(occurrenceRepository.findFuturePendingByFixedPaymentId(eq(1), any(LocalDate.class)))
                    .thenReturn(List.of(futureOcc1, futureOcc2));

            // when
            fixedPaymentCrudService.deactivateFixedPayment(1, USER_ID);

            // then
            assertThat(existing.getActiveTo()).isEqualTo(TODAY);
            verify(occurrenceRepository).deleteAll(List.of(futureOcc1, futureOcc2));
            verify(fixedPaymentRepository).save(existing);
        }

        @Test
        void noFuturePending_stillSetsActiveToAndSaves() {
            // given
            FixedPayment existing = buildFixedPayment(1);

            when(fixedPaymentRepository.findByIdAndWalletUserId(1, USER_ID))
                    .thenReturn(Optional.of(existing));
            when(occurrenceRepository.findFuturePendingByFixedPaymentId(eq(1), any(LocalDate.class)))
                    .thenReturn(List.of());

            // when
            fixedPaymentCrudService.deactivateFixedPayment(1, USER_ID);

            // then
            assertThat(existing.getActiveTo()).isEqualTo(TODAY);
            verify(fixedPaymentRepository).save(existing);
        }

        @Test
        void notFound_throwsEntityNotFound() {
            // given
            when(fixedPaymentRepository.findByIdAndWalletUserId(999, USER_ID))
                    .thenReturn(Optional.empty());

            // when / then
            // when
            // then
            assertThatThrownBy(() -> fixedPaymentCrudService.deactivateFixedPayment(999, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class GetAllFixedPayments {

        @Test
        void returnsResponsesForUserWallet() {
            // given
            FixedPayment fp1 = buildFixedPayment(1);
            FixedPayment fp2 = buildFixedPayment(2);
            fp2.setTitle("Internet");
            fp2.setAmount(new BigDecimal("60.00"));

            when(fixedPaymentRepository.findAllByWalletIdAndUserId(1, USER_ID))
                    .thenReturn(List.of(fp1, fp2));

            // when
            List<FixedPaymentResponse> result = fixedPaymentCrudService.getAllFixedPayments(1, USER_ID);

            // then
            assertThat(result).hasSize(2);
            assertThat(result)
                    .extracting(FixedPaymentResponse::title)
                    .containsExactly("Monthly Rent", "Internet");
        }

        @Test
        void noFixedPayments_returnsEmptyList() {
            // given
            when(fixedPaymentRepository.findAllByWalletIdAndUserId(1, USER_ID)).thenReturn(List.of());

            // when
            List<FixedPaymentResponse> result = fixedPaymentCrudService.getAllFixedPayments(1, USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }
}
