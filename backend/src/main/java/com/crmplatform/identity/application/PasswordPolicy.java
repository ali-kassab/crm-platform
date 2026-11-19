package com.crmplatform.identity.application;

import com.crmplatform.shared.error.ApiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Password rules (VALIDATION_AND_ERROR_HANDLING.md §4): 10–128 characters, at least one letter
 * and one digit, not derived from the email, not among the 10,000 most common passwords
 * (list: SecLists, MIT licence).
 */
@Component
public class PasswordPolicy {

    private final Set<String> common;

    public PasswordPolicy() {
        this.common = load();
    }

    public void validate(String password, String email) {
        if (password == null || password.length() < 10 || password.length() > 128) {
            throw invalid("PASSWORD_TOO_SHORT", "Password must be 10 to 128 characters long");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw invalid("PASSWORD_TOO_WEAK", "Password must contain at least one letter and one digit");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (email != null) {
            String localPart = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (lower.equals(email.toLowerCase(Locale.ROOT)) || (localPart.length() >= 4 && lower.contains(localPart))) {
                throw invalid("PASSWORD_TOO_WEAK", "Password must not contain your email address");
            }
        }
        if (common.contains(lower)) {
            throw invalid("PASSWORD_COMMON", "This password is too common");
        }
    }

    private static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                Map.of("errors", java.util.List.of(Map.of("field", "password", "code", code, "message", message))));
    }

    private static Set<String> load() {
        Set<String> words = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("security/common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    words.add(line.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Common password list missing", e);
        }
        return Set.copyOf(words);
    }
}
