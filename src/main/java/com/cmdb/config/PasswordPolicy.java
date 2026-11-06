package com.cmdb.config;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class PasswordPolicy {
    public static final int MAX_AGE_MONTHS = 3;
    public static final int WARNING_DAYS = 14;
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) return false;
        boolean lower = false, upper = false, digit = false, special = false;
        for (int i = 0; i < password.length(); i++) {
            char value = password.charAt(i);
            if (Character.isWhitespace(value)) return false;
            if (Character.isLowerCase(value)) lower = true;
            else if (Character.isUpperCase(value)) upper = true;
            else if (Character.isDigit(value)) digit = true;
            else special = true;
        }
        return lower && upper && digit && special;
    }

    public static LocalDateTime expiresAt(LocalDateTime changedAt) {
        return changedAt == null ? null : changedAt.plusMonths(MAX_AGE_MONTHS);
    }

    public static int daysRemaining(LocalDateTime changedAt, LocalDateTime now) {
        LocalDateTime expiresAt = expiresAt(changedAt);
        return expiresAt == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(now, expiresAt));
    }

    public static boolean isExpired(LocalDateTime changedAt, LocalDateTime now) {
        LocalDateTime expiresAt = expiresAt(changedAt);
        return expiresAt == null || !now.isBefore(expiresAt);
    }
}
