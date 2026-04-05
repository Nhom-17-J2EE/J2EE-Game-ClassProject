package com.game.hub.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Centralized password-strength validation used by every entry point that
 * accepts a new or changed password (registration, change-password,
 * reset-password, admin/manager user creation).
 *
 * <p>Rules enforced:
 * <ol>
 *   <li>Minimum {@value #MIN_LENGTH} characters</li>
 *   <li>At least one uppercase letter (A-Z)</li>
 *   <li>At least one lowercase letter (a-z)</li>
 *   <li>At least one digit (0-9)</li>
 *   <li>At least one special character (!@#$%^&amp;*…)</li>
 * </ol>
 */
public final class PasswordValidator {

    public static final int MIN_LENGTH = 8;

    private static final Pattern HAS_UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT     = Pattern.compile("[0-9]");
    private static final Pattern HAS_SPECIAL   = Pattern.compile("[^A-Za-z0-9]");

    private PasswordValidator() { /* utility class */ }

    /**
     * Validates the given password against all rules.
     *
     * @param password the raw password to validate
     * @return an <b>unmodifiable</b> list of human-readable error messages;
     *         empty list means the password is valid.
     */
    public static List<String> validate(String password) {
        if (password == null || password.isBlank()) {
            return List.of("Password is required");
        }

        List<String> errors = new ArrayList<>();

        if (password.length() < MIN_LENGTH) {
            errors.add("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (!HAS_UPPERCASE.matcher(password).find()) {
            errors.add("Password must contain at least one uppercase letter");
        }
        if (!HAS_LOWERCASE.matcher(password).find()) {
            errors.add("Password must contain at least one lowercase letter");
        }
        if (!HAS_DIGIT.matcher(password).find()) {
            errors.add("Password must contain at least one digit");
        }
        if (!HAS_SPECIAL.matcher(password).find()) {
            errors.add("Password must contain at least one special character");
        }

        return Collections.unmodifiableList(errors);
    }

    /**
     * Convenience check – returns {@code true} when the password satisfies
     * every rule.
     */
    public static boolean isValid(String password) {
        return validate(password).isEmpty();
    }
}
