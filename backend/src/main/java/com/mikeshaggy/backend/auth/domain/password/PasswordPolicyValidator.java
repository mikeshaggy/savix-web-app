package com.mikeshaggy.backend.auth.domain.password;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PasswordPolicyValidator {

    private static final Pattern LOWER_CASE = Pattern.compile("[a-z]");
    private static final Pattern UPPER_CASE = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL_CHAR = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    // TODO: implement haveibeenpwned API check for breached passwords
    private static final List<String> COMMON_PASSWORDS = Arrays.asList(
            "password", "123456", "123456789", "12345678", "12345", "1234567", "password1",
            "qwerty", "abc123", "111111", "123123", "admin", "letmein", "welcome",
            "monkey", "dragon", "master", "sunshine", "princess", "password123"
    );

    @Value("${auth.password.min-length:12}")
    private int minLength;

    @Value("${auth.password.require-categories:3}")
    private int requiredCategories;

    public ValidationResult validate(String password) {
        if (password == null || password.length() < minLength) {
            return ValidationResult.invalid(
                    String.format("Password must be at least %d characters long", minLength)
            );
        }

        if (COMMON_PASSWORDS.contains(password.toLowerCase())) {
            return ValidationResult.invalid("Password is too common and easily guessable");
        }

        int categoriesPresent = 0;
        if (LOWER_CASE.matcher(password).find()) categoriesPresent++;
        if (UPPER_CASE.matcher(password).find()) categoriesPresent++;
        if (DIGIT.matcher(password).find()) categoriesPresent++;
        if (SPECIAL_CHAR.matcher(password).find()) categoriesPresent++;

        if (categoriesPresent < requiredCategories) {
            return ValidationResult.invalid(
                    String.format("Password must contain at least %d of the following: " +
                                    "lowercase letters, uppercase letters, digits, special characters",
                            requiredCategories)
            );
        }

        return ValidationResult.valid();
    }

    @Getter
    @RequiredArgsConstructor
    public static class ValidationResult {

        private final boolean valid;
        private final String message;

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
