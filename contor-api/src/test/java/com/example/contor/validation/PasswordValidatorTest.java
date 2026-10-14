package com.example.contor.validation;

import com.example.contor.exception.PasswordValidationException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    @Test
    void accepts_a_strong_password() {
        assertThatCode(() -> validator.validate("Correct-Horse-7-Battery")).doesNotThrowAnyException();
    }

    @Test
    void reports_all_failing_rules_together() {
        var exception = catchThrowableOfType(PasswordValidationException.class, () -> validator.validate("short"));

        assertThat(exception.getViolations()).containsExactly(
                "Password must be at least 12 characters long",
                "Password must contain an uppercase letter",
                "Password must contain a digit",
                "Password must contain a special character");
        assertThat(exception.getMessage()).isEqualTo(String.join("; ", exception.getViolations()));
    }

    @Test
    void rejects_missing_lowercase() {
        var exception = catchThrowableOfType(PasswordValidationException.class, () -> validator.validate("ALLUPPER-1234"));

        assertThat(exception.getViolations()).containsExactly("Password must contain a lowercase letter");
    }

    @Test
    void rejects_too_long_password() {
        var exception = catchThrowableOfType(PasswordValidationException.class, () -> validator.validate("Aa1!" + "x".repeat(125)));

        assertThat(exception.getViolations()).containsExactly("Password must be at most 128 characters long");
    }

    @Test
    void rejects_common_passwords_case_insensitively() {
        var exception = catchThrowableOfType(PasswordValidationException.class, () -> validator.validate("Password123!"));

        assertThat(exception.getViolations()).containsExactly("Password is too common");
    }

    @Test
    void rejects_null_password() {
        var exception = catchThrowableOfType(PasswordValidationException.class, () -> validator.validate(null));

        assertThat(exception.getViolations()).containsExactly("Password is required");
    }
}
