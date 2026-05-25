package com.mikeshaggy.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.auth.domain.ratelimit.RateLimitEntry;
import com.mikeshaggy.backend.auth.repository.RateLimitRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RateLimitRepository rateLimitRepository;

    @InjectMocks
    private RateLimitService rateLimitService;

    private static final int MAX_ATTEMPTS = 5;
    private static final int WINDOW_SECONDS = 300;
    private static final int LOCKOUT_SECONDS = 900;
    private static final String TYPE = "login:ip";
    private static final String IDENTIFIER = "192.168.1.1";
    private static final String KEY = TYPE + ":" + IDENTIFIER;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimitService, "maxAttempts", MAX_ATTEMPTS);
        ReflectionTestUtils.setField(rateLimitService, "windowSeconds", WINDOW_SECONDS);
        ReflectionTestUtils.setField(rateLimitService, "lockoutSeconds", LOCKOUT_SECONDS);
    }

    @Nested
    class IsAllowed {

        @Test
        void firstAttemptIsAllowedAndCreatesNewEntry() {
            // given
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.empty());

            // when
            boolean result = rateLimitService.isAllowed(TYPE, IDENTIFIER);

            // then
            assertThat(result).isTrue();
            ArgumentCaptor<RateLimitEntry> captor = ArgumentCaptor.forClass(RateLimitEntry.class);
            verify(rateLimitRepository).save(captor.capture());
            RateLimitEntry saved = captor.getValue();
            assertThat(saved.getKey()).isEqualTo(KEY);
            assertThat(saved.getAttempts()).isEqualTo(1);
            assertThat(saved.getTtl()).isEqualTo((long) WINDOW_SECONDS);
        }

        @Test
        void withinWindowUnderLimitIsAllowedAndIncrementsCount() {
            // given
            RateLimitEntry entry = entryWithAttemptsInWindow(3);
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(entry));

            // when
            boolean result = rateLimitService.isAllowed(TYPE, IDENTIFIER);

            // then
            assertThat(result).isTrue();
            ArgumentCaptor<RateLimitEntry> captor = ArgumentCaptor.forClass(RateLimitEntry.class);
            verify(rateLimitRepository).save(captor.capture());
            assertThat(captor.getValue().getAttempts()).isEqualTo(4);
        }

        @Test
        void oneAttemptBelowLimitIsStillAllowed() {
            // given
            RateLimitEntry entry = entryWithAttemptsInWindow(MAX_ATTEMPTS - 1);
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(entry));

            // when
            boolean result = rateLimitService.isAllowed(TYPE, IDENTIFIER);

            // then
            assertThat(result).isTrue();
            ArgumentCaptor<RateLimitEntry> captor = ArgumentCaptor.forClass(RateLimitEntry.class);
            verify(rateLimitRepository).save(captor.capture());
            assertThat(captor.getValue().getAttempts()).isEqualTo(MAX_ATTEMPTS);
        }

        @Test
        void atMaxAttemptsRejectsAndSetsLockout() {
            // given
            RateLimitEntry entry = entryWithAttemptsInWindow(MAX_ATTEMPTS);
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(entry));

            // when
            boolean result = rateLimitService.isAllowed(TYPE, IDENTIFIER);

            // then
            assertThat(result).isFalse();
            ArgumentCaptor<RateLimitEntry> captor = ArgumentCaptor.forClass(RateLimitEntry.class);
            verify(rateLimitRepository).save(captor.capture());
            RateLimitEntry saved = captor.getValue();
            assertThat(saved.getLockedUntil()).isNotNull();
            assertThat(saved.getTtl()).isEqualTo((long) (LOCKOUT_SECONDS + WINDOW_SECONDS));
        }

        @Test
        void duringLockoutIsRejected() {
            // given
            RateLimitEntry entry = lockedEntry();
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(entry));

            // when
            boolean result = rateLimitService.isAllowed(TYPE, IDENTIFIER);

            // then
            assertThat(result).isFalse();
            verify(rateLimitRepository, never()).save(any());
        }

        @Test
        void afterWindowExpiresResetsAndAllows() {
            // given
            RateLimitEntry entry = expiredWindowEntry();
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(entry));

            // when
            boolean result = rateLimitService.isAllowed(TYPE, IDENTIFIER);

            // then
            assertThat(result).isTrue();
            ArgumentCaptor<RateLimitEntry> captor = ArgumentCaptor.forClass(RateLimitEntry.class);
            verify(rateLimitRepository).save(captor.capture());
            assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        }

        @Test
        void afterLockoutAndWindowExpireResetsAndAllows() {
            // given
            RateLimitEntry entry =
                    RateLimitEntry.builder()
                            .key(KEY)
                            .attempts(MAX_ATTEMPTS)
                            .firstAttemptAt(Instant.now().minusSeconds(WINDOW_SECONDS + 100))
                            .lastAttemptAt(Instant.now().minusSeconds(LOCKOUT_SECONDS + 50))
                            .lockedUntil(Instant.now().minusSeconds(50))
                            .ttl((long) (LOCKOUT_SECONDS + WINDOW_SECONDS))
                            .build();
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(entry));

            // when
            boolean result = rateLimitService.isAllowed(TYPE, IDENTIFIER);

            // then
            assertThat(result).isTrue();
            ArgumentCaptor<RateLimitEntry> captor = ArgumentCaptor.forClass(RateLimitEntry.class);
            verify(rateLimitRepository).save(captor.capture());
            assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        }

        @Test
        void customParametersOverrideDefaults() {
            // given
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.empty());

            int customWindow = 60;
            // when
            boolean result = rateLimitService.isAllowed(TYPE, IDENTIFIER, 10, customWindow, 120);

            // then
            assertThat(result).isTrue();
            ArgumentCaptor<RateLimitEntry> captor = ArgumentCaptor.forClass(RateLimitEntry.class);
            verify(rateLimitRepository).save(captor.capture());
            assertThat(captor.getValue().getTtl()).isEqualTo((long) customWindow);
        }

        @Test
        void customMaxAttemptsAppliedToLockoutDecision() {
            // given
            int customMax = 2;
            RateLimitEntry entry = entryWithAttemptsInWindow(customMax);
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(entry));

            // when
            boolean result =
                    rateLimitService.isAllowed(TYPE, IDENTIFIER, customMax, WINDOW_SECONDS, LOCKOUT_SECONDS);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class RecordSuccess {

        @Test
        void deletesEntryForKey() {
            // given
            // when
            rateLimitService.recordSuccess(TYPE, IDENTIFIER);

            // then
            verify(rateLimitRepository).deleteById(KEY);
        }
    }

    @Nested
    class GetRemainingAttempts {

        @Test
        void noEntryReturnsMaxAttempts() {
            // given
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.empty());

            // when
            int remaining = rateLimitService.getRemainingAttempts(TYPE, IDENTIFIER);

            // then
            assertThat(remaining).isEqualTo(MAX_ATTEMPTS);
        }

        @Test
        void duringLockoutReturnsZero() {
            // given
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(lockedEntry()));

            // when
            int remaining = rateLimitService.getRemainingAttempts(TYPE, IDENTIFIER);

            // then
            assertThat(remaining).isEqualTo(0);
        }

        @Test
        void expiredWindowReturnsMaxAttempts() {
            // given
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(expiredWindowEntry()));

            // when
            int remaining = rateLimitService.getRemainingAttempts(TYPE, IDENTIFIER);

            // then
            assertThat(remaining).isEqualTo(MAX_ATTEMPTS);
        }

        @Test
        void partiallyUsedReturnsCorrectCount() {
            // given
            when(rateLimitRepository.findById(KEY)).thenReturn(Optional.of(entryWithAttemptsInWindow(3)));

            // when
            int remaining = rateLimitService.getRemainingAttempts(TYPE, IDENTIFIER);

            // then
            assertThat(remaining).isEqualTo(MAX_ATTEMPTS - 3);
        }

        @Test
        void fullyUsedReturnsZero() {
            // given
            when(rateLimitRepository.findById(KEY))
                    .thenReturn(Optional.of(entryWithAttemptsInWindow(MAX_ATTEMPTS)));

            // when
            int remaining = rateLimitService.getRemainingAttempts(TYPE, IDENTIFIER);

            // then
            assertThat(remaining).isEqualTo(0);
        }
    }

    private RateLimitEntry entryWithAttemptsInWindow(int attempts) {
        return RateLimitEntry.builder()
                .key(KEY)
                .attempts(attempts)
                .firstAttemptAt(Instant.now().minusSeconds(10))
                .lastAttemptAt(Instant.now().minusSeconds(5))
                .ttl((long) WINDOW_SECONDS)
                .build();
    }

    private RateLimitEntry lockedEntry() {
        return RateLimitEntry.builder()
                .key(KEY)
                .attempts(MAX_ATTEMPTS)
                .firstAttemptAt(Instant.now().minusSeconds(60))
                .lastAttemptAt(Instant.now().minusSeconds(30))
                .lockedUntil(Instant.now().plusSeconds(LOCKOUT_SECONDS))
                .ttl((long) (LOCKOUT_SECONDS + WINDOW_SECONDS))
                .build();
    }

    private RateLimitEntry expiredWindowEntry() {
        return RateLimitEntry.builder()
                .key(KEY)
                .attempts(3)
                .firstAttemptAt(Instant.now().minusSeconds(WINDOW_SECONDS + 100))
                .lastAttemptAt(Instant.now().minusSeconds(WINDOW_SECONDS + 50))
                .ttl((long) WINDOW_SECONDS)
                .build();
    }
}
