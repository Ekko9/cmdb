package com.cmdb.config;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class PasswordPolicy {
    public static final int MAX_AGE_MONTHS = 3;
    public static final int WARNING_DAYS = 14;

    private PasswordPolicy() {
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
