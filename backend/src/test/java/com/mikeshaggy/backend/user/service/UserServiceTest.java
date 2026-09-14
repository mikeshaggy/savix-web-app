package com.mikeshaggy.backend.user.service;

import com.mikeshaggy.backend.common.paycycle.WeekendShift;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.domain.UserPaydayRule;
import com.mikeshaggy.backend.user.dto.MeFeaturesResponse;
import com.mikeshaggy.backend.user.dto.MeResponse;
import com.mikeshaggy.backend.user.dto.MeUpdateRequest;
import com.mikeshaggy.backend.user.dto.PaydayRuleRequest;
import com.mikeshaggy.backend.user.dto.PaydayRuleResponse;
import com.mikeshaggy.backend.user.repository.UserPaydayRuleRepository;
import com.mikeshaggy.backend.user.repository.UserRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final FeatureFlags FLAGS = new FeatureFlags(true, false, true, false, true);

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserPaydayRuleRepository paydayRuleRepository;

    private UserService service;
    private User existingUser;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, walletRepository, paydayRuleRepository, FLAGS);
        existingUser = User.builder()
                .id(USER_ID)
                .email("fx@example.com")
                .username("fx-user")
                .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
    }

    @Test
    void getUserById_surfacesFlagsButNeverForecastV2Shadow() {
        // given: forecastV2Shadow is true (backend-only) alongside two client-visible flags
        when(paydayRuleRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when
        MeResponse response = service.getUserById(USER_ID);

        // then
        assertThat(response.features()).isEqualTo(new MeFeaturesResponse(true, false, false, true));
        assertThat(response.salaryWalletId()).isNull();
        assertThat(response.paydayRule()).isNull();
    }

    @Test
    void getUserById_returnsSalaryWalletIdAndConfiguredPaydayRule() {
        // given
        existingUser.setSalaryWallet(wallet(7, false));
        when(paydayRuleRepository.findById(USER_ID)).thenReturn(Optional.of(UserPaydayRule.builder()
                .userId(USER_ID)
                .dayOfMonth(10)
                .weekendShift(WeekendShift.PREVIOUS_BUSINESS_DAY)
                .build()));

        // when
        MeResponse response = service.getUserById(USER_ID);

        // then
        assertThat(response.salaryWalletId()).isEqualTo(7);
        assertThat(response.paydayRule()).isEqualTo(new PaydayRuleResponse(10, WeekendShift.PREVIOUS_BUSINESS_DAY));
    }

    @Test
    void updateUserById_returnsSameFeaturesShapeAsGetUserById() {
        // given: PATCH /api/me must carry the same features shape as GET /api/me (AC-3)
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(paydayRuleRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when
        MeResponse response = service.updateUserById(USER_ID, new MeUpdateRequest("fx-user-renamed", null));

        // then
        assertThat(response.features()).isEqualTo(new MeFeaturesResponse(true, false, false, true));
        assertThat(existingUser.getUsername()).isEqualTo("fx-user-renamed");
        assertThat(existingUser.getSalaryWallet()).isNull();
    }

    @Test
    void updateUserById_setsSalaryWalletWithoutTouchingUsername() {
        // given
        Wallet daily = wallet(1, false);
        when(walletRepository.findByIdAndUserId(1, USER_ID)).thenReturn(Optional.of(daily));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(paydayRuleRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when
        MeResponse response = service.updateUserById(USER_ID, new MeUpdateRequest(null, 1));

        // then
        assertThat(existingUser.getSalaryWallet()).isSameAs(daily);
        assertThat(existingUser.getUsername()).isEqualTo("fx-user");
        assertThat(response.salaryWalletId()).isEqualTo(1);
    }

    @Test
    void updateUserById_foreignWallet_throwsNotFound() {
        // given: wallet 99 does not belong to the user
        when(walletRepository.findByIdAndUserId(99, USER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.updateUserById(USER_ID, new MeUpdateRequest(null, 99)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserById_fundWallet_throwsBadRequest() {
        // given
        when(walletRepository.findByIdAndUserId(5, USER_ID)).thenReturn(Optional.of(wallet(5, true)));

        // when / then
        assertThatThrownBy(() -> service.updateUserById(USER_ID, new MeUpdateRequest(null, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fund");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserById_blankUsername_throwsBadRequest() {
        assertThatThrownBy(() -> service.updateUserById(USER_ID, new MeUpdateRequest("   ", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setPaydayRule_createsRuleWithDefaultShiftWhenAbsent() {
        // given: no rule yet, weekendShift omitted
        when(paydayRuleRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(paydayRuleRepository.save(any(UserPaydayRule.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        MeResponse response = service.setPaydayRule(USER_ID, new PaydayRuleRequest(10, null));

        // then
        ArgumentCaptor<UserPaydayRule> captor = ArgumentCaptor.forClass(UserPaydayRule.class);
        verify(paydayRuleRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getDayOfMonth()).isEqualTo(10);
        assertThat(captor.getValue().getWeekendShift()).isEqualTo(WeekendShift.PREVIOUS_BUSINESS_DAY);
        assertThat(response.paydayRule()).isEqualTo(new PaydayRuleResponse(10, WeekendShift.PREVIOUS_BUSINESS_DAY));
    }

    @Test
    void setPaydayRule_updatesExistingRuleInPlace() {
        // given
        UserPaydayRule existing = UserPaydayRule.builder()
                .userId(USER_ID)
                .dayOfMonth(10)
                .weekendShift(WeekendShift.PREVIOUS_BUSINESS_DAY)
                .build();
        when(paydayRuleRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(paydayRuleRepository.save(any(UserPaydayRule.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        MeResponse response = service.setPaydayRule(USER_ID, new PaydayRuleRequest(25, WeekendShift.NEXT_BUSINESS_DAY));

        // then
        verify(paydayRuleRepository).save(existing);
        assertThat(existing.getDayOfMonth()).isEqualTo(25);
        assertThat(existing.getWeekendShift()).isEqualTo(WeekendShift.NEXT_BUSINESS_DAY);
        assertThat(response.paydayRule()).isEqualTo(new PaydayRuleResponse(25, WeekendShift.NEXT_BUSINESS_DAY));
    }

    @Test
    void deletePaydayRule_removesRuleAndReturnsNullRule() {
        // when
        MeResponse response = service.deletePaydayRule(USER_ID);

        // then
        verify(paydayRuleRepository).deleteById(USER_ID);
        assertThat(response.paydayRule()).isNull();
        assertThat(response.features()).isEqualTo(new MeFeaturesResponse(true, false, false, true));
    }

    private static Wallet wallet(int id, boolean fund) {
        return Wallet.builder().id(id).name("w" + id).isFund(fund).build();
    }
}
