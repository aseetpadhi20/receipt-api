package com.receipts.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    private static final String KEY = "receipts/2026/09/abc-receipt.txt";

    @TempDir
    Path root;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(root);
    }

    @Test
    void storesAndLoadsFile() throws IOException {
        storage.store(KEY, stream("hello"), 5, "text/plain");

        try (InputStream in = storage.load(KEY)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }
        assertThat(root.resolve(KEY)).exists();
    }

    @Test
    void leavesNoTempFilesBehind() throws IOException {
        storage.store(KEY, stream("hello"), 5, "text/plain");

        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(p -> p.getFileName().toString().endsWith(".tmp"))).isEmpty();
        }
    }

    @Test
    void deleteRemovesFileAndIgnoresMissingFile() {
        storage.store(KEY, stream("hello"), 5, "text/plain");

        storage.delete(KEY);
        storage.delete(KEY);

        assertThat(root.resolve(KEY)).doesNotExist();
    }

    @Test
    void loadingMissingFileThrowsNotFound() {
        assertThatThrownBy(() -> storage.load("receipts/2026/09/missing.txt"))
                .isInstanceOf(StorageFileNotFoundException.class);
    }

    @Test
    void keysOutsideRootAreRejected() {
        assertThatThrownBy(() -> storage.store("../outside.txt", stream("x"), 1, "text/plain"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Invalid storage key");
        assertThatThrownBy(() -> storage.load("receipts/../../outside.txt"))
                .isInstanceOf(StorageException.class);
        assertThat(root.getParent().resolve("outside.txt")).doesNotExist();
    }

    @Test
    void backendNameIsLocal() {
        assertThat(storage.backendName()).isEqualTo("local");
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
