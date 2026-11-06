package com.cmdb.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpAddressValidatorTest {
    @Test
    void acceptsIpv4AndIpv6Literals() {
        assertTrue(IpAddressValidator.isValid("192.168.10.20"));
        assertTrue(IpAddressValidator.isValid("2001:db8::1"));
    }

    @Test
    void rejectsOutOfRangeAndMalformedAddresses() {
        assertFalse(IpAddressValidator.isValid("999.999.999.999"));
        assertFalse(IpAddressValidator.isValid("10.0.0"));
        assertFalse(IpAddressValidator.isValid("::::"));
        assertFalse(IpAddressValidator.isValid("example.com"));
    }

    @Test
    void canonicalizesEquivalentIpForms() {
        assertTrue(IpAddressValidator.canonicalize("010.000.000.001").equals("10.0.0.1"));
        assertTrue(IpAddressValidator.canonicalize("2001:0db8:0:0:0:0:0:1")
                .equals(IpAddressValidator.canonicalize("2001:db8::1")));
    }
}
