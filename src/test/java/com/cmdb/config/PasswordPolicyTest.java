package com.cmdb.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    @Test
    void expiresAfterNinetyDaysAndWarnsWithinFourteenDays() {
        LocalDateTime changedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime warningBoundary = changedAt.plusMonths(3).minusDays(14);
        LocalDateTime expiresAt = changedAt.plusMonths(3);

        assertFalse(PasswordPolicy.isExpired(changedAt, warningBoundary));
        assertTrue(PasswordPolicy.daysRemaining(changedAt, warningBoundary) <= 14);
        assertTrue(PasswordPolicy.isExpired(changedAt, expiresAt));
        assertEquals(0, PasswordPolicy.daysRemaining(changedAt, expiresAt));
    }

    @Test
    void missingPasswordTimestampRequiresChange() {
        assertTrue(PasswordPolicy.isExpired(null, LocalDateTime.now()));
        assertEquals(0, PasswordPolicy.daysRemaining(null, LocalDateTime.now()));
    }

    @Test
    void validatesComplexityAndBcryptLengthLimit() {
        assertTrue(PasswordPolicy.isValid("StrongPass1!"));
        assertFalse(PasswordPolicy.isValid("weak"));
        assertFalse(PasswordPolicy.isValid("Strong Pass1!"));
        assertFalse(PasswordPolicy.isValid("Aa1!" + "x".repeat(69)));
    }
}
