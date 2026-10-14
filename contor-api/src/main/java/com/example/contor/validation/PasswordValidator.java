package com.example.contor.validation;

import com.example.contor.exception.PasswordValidationException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Password rules: 12-128 characters, lowercase, uppercase, digit, special character, not a common password.
 * Collects every violated rule and reports them together.
 */
@Component
public class PasswordValidator {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;
    private static final String COMMON_PASSWORDS = "security/common-passwords.txt";

    private final Set<String> commonPasswords = loadCommonPasswords();

    public void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw new PasswordValidationException(List.of("Password is required"));
        }
        List<String> violations = new ArrayList<>();
        if (password.length() < MIN_LENGTH) violations.add("Password must be at least " + MIN_LENGTH + " characters long");
        if (password.length() > MAX_LENGTH) violations.add("Password must be at most " + MAX_LENGTH + " characters long");
        if (password.chars().noneMatch(Character::isLowerCase)) violations.add("Password must contain a lowercase letter");
        if (password.chars().noneMatch(Character::isUpperCase)) violations.add("Password must contain an uppercase letter");
        if (password.chars().noneMatch(Character::isDigit)) violations.add("Password must contain a digit");
        if (password.chars().allMatch(Character::isLetterOrDigit)) violations.add("Password must contain a special character");
        if (commonPasswords.contains(password.toLowerCase(Locale.ROOT))) violations.add("Password is too common");
        if (!violations.isEmpty()) {
            throw new PasswordValidationException(violations);
        }
    }

    private static Set<String> loadCommonPasswords() {
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(COMMON_PASSWORDS).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + COMMON_PASSWORDS, e);
        }
    }
}
