package com.cmdb.config;

public final class CsvExportSanitizer {
    private CsvExportSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) return value;
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        if (index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
