package com.receipts.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class StorageKeysTest {

    @Test
    void keyHasYearMonthUuidAndFilename() {
        String key = StorageKeys.newKey("receipt-clean.png", Instant.parse("2026-09-27T10:00:00Z"));

        assertThat(key).matches("receipts/2026/09/[0-9a-f-]{36}-receipt-clean\\.png");
    }

    @Test
    void keysAreUnique() {
        Instant now = Instant.now();

        assertThat(StorageKeys.newKey("a.png", now)).isNotEqualTo(StorageKeys.newKey("a.png", now));
    }

    @Test
    void pathPartsAreRemoved() {
        assertThat(StorageKeys.cleanFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(StorageKeys.cleanFilename("C:\\Users\\me\\receipt.pdf")).isEqualTo("receipt.pdf");
    }

    @Test
    void unsafeCharactersAreReplaced() {
        assertThat(StorageKeys.cleanFilename("my receipt (1).png")).isEqualTo("my_receipt__1_.png");
    }

    @Test
    void leadingDotsAreRemoved() {
        assertThat(StorageKeys.cleanFilename("..")).isEqualTo("file");
        assertThat(StorageKeys.cleanFilename(".hidden.txt")).isEqualTo("hidden.txt");
    }

    @Test
    void emptyOrNullBecomesFile() {
        assertThat(StorageKeys.cleanFilename("")).isEqualTo("file");
        assertThat(StorageKeys.cleanFilename(null)).isEqualTo("file");
    }

    @Test
    void longNamesAreCut() {
        assertThat(StorageKeys.cleanFilename("a".repeat(300) + ".png")).hasSize(100).endsWith(".png");
    }
}
