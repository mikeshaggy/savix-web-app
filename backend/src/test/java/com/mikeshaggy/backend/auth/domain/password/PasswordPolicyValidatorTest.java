package com.mikeshaggy.backend.auth.domain.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator();
        ReflectionTestUtils.setField(validator, "minLength", 12);
        ReflectionTestUtils.setField(validator, "requiredCategories", 3);
    }

    @Nested
    class ValidPasswords {

        @Test
        void meetsAllRequirements() {
            // given
            // when
            var result = validator.validate("MyStr0ngP@ssword");
            // then
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).isNull();
        }

        @Test
        void exactMinLength_withThreeCategories() {
            // 12 chars: lower + upper + digit = 3 categories
            // given
            // when
            var result = validator.validate("Abcdefghij1k");
            // then
            assertThat(result.isValid()).isTrue();
        }

        @Test
        void allFourCategories() {
            // given
            // when
            var result = validator.validate("Abc123!@#defgh");
            // then
            assertThat(result.isValid()).isTrue();
        }

        @Test
        void threeCategories_lowerUpperSpecial() {
            // given
            // when
            var result = validator.validate("Abcdef!@#ghijk");
            // then
            assertThat(result.isValid()).isTrue();
        }

        @Test
        void threeCategories_lowerDigitSpecial() {
            // given
            // when
            var result = validator.validate("abcdef123!@#g");
            // then
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    class InvalidPasswords {

        @Test
        void nullPassword() {
            // given
            // when
            var result = validator.validate(null);
            // then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("at least 12 characters");
        }

        @Test
        void tooShort() {
            // given
            // when
            var result = validator.validate("Abc1!fgh");
            // then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("at least 12 characters");
        }

        @Test
        void emptyString() {
            // given
            // when
            var result = validator.validate("");
            // then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("at least 12 characters");
        }

        @Test
        void commonPassword_password() {
            // common passwords in the list are all < 12 chars, so lower minLength to test the check
            // given
            ReflectionTestUtils.setField(validator, "minLength", 6);
            // when
            var result = validator.validate("password");
            // then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("too common");
        }

        @Test
        void commonPassword_caseInsensitive() {
            // given
            ReflectionTestUtils.setField(validator, "minLength", 6);
            // when
            var result = validator.validate("PASSWORD");
            // then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("too common");
        }

        @Test
        void commonPassword_qwerty() {
            // given
            ReflectionTestUtils.setField(validator, "minLength", 6);
            // when
            var result = validator.validate("qwerty");
            // then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("too common");
        }

        @Test
        void onlyOneCategory_lowercase() {
            // given
            // when
            var result = validator.validate("abcdefghijklmn");
            // then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("at least 3");
        }

        @Test
        void twoCategories_lowerAndUpper() {
            // given
            // when
            var result = validator.validate("AbcdefGHIJKLmn");
            // then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("at least 3");
        }

        @Test
        void onlyDigits() {
            // given
            // when
            var result = validator.validate("123456789012");
            // then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("at least 3");
        }
    }

    @Nested
    class ConfigurableRequirements {

        @Test
        void customMinLength() {
            // given
            ReflectionTestUtils.setField(validator, "minLength", 8);
            // when
            var result = validator.validate("Abc1!fgh");
            // then
            assertThat(result.isValid()).isTrue();
        }

        @Test
        void customRequiredCategories_two() {
            // given
            ReflectionTestUtils.setField(validator, "requiredCategories", 2);
            // when
            var result = validator.validate("AbcdefGHIJKLmn");
            // then
            assertThat(result.isValid()).isTrue();
        }
    }
}
