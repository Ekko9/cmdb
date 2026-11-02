package com.cmdb.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CsvExportSanitizerTest {
    @Test
    void prefixesFormulaCharactersAfterLeadingWhitespace() {
        assertEquals("'=1+1", CsvExportSanitizer.sanitize("=1+1"));
        assertEquals("'  @SUM(A1:A2)", CsvExportSanitizer.sanitize("  @SUM(A1:A2)"));
        assertEquals("ordinary text", CsvExportSanitizer.sanitize("ordinary text"));
        assertNull(CsvExportSanitizer.sanitize(null));
    }
}
