package com.cmdb.config;

import java.net.InetAddress;

public final class IpAddressValidator {
    private IpAddressValidator() {
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.length() > 45) return false;
        if (value.indexOf(':') >= 0) return isValidIpv6(value);
        return isValidIpv4(value);
    }

    public static String canonicalize(String value) {
        if (!isValid(value)) throw new IllegalArgumentException("invalid IP address");
        if (value.indexOf(':') < 0) {
            String[] parts = value.split("\\.");
            return String.join(".", java.util.Arrays.stream(parts)
                    .map(part -> Integer.toString(Integer.parseInt(part))).toList());
        }
        try {
            byte[] address = InetAddress.getByName(value).getAddress();
            if (address.length != 16) return value;
            StringBuilder result = new StringBuilder(39);
            for (int i = 0; i < address.length; i += 2) {
                if (i > 0) result.append(':');
                int word = (Byte.toUnsignedInt(address[i]) << 8) | Byte.toUnsignedInt(address[i + 1]);
                result.append(Integer.toHexString(word));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid IP address", exception);
        }
    }

    private static boolean isValidIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) return false;
            }
            int number;
            try {
                number = Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                return false;
            }
            if (number < 0 || number > 255) return false;
        }
        return true;
    }

    private static boolean isValidIpv6(String value) {
        if (!value.matches("^[0-9A-Fa-f:.]+$")) return false;
        try {
            return InetAddress.getByName(value).getHostAddress().contains(":");
        } catch (Exception exception) {
            return false;
        }
    }
}
