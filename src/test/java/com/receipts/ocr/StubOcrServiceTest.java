package com.receipts.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class StubOcrServiceTest {

    private static final Path FIXTURES = Path.of("fixtures", "task-a");

    private final StubOcrService ocr = new StubOcrService(FIXTURES);

    @Test
    void textUploadIsUsedAsOcrText() {
        String text = ocr.extractText("anything.txt", "text/plain", stream("TOTAL   1.00"));

        assertThat(text).isEqualTo("TOTAL   1.00");
    }

    @Test
    void textUploadIsDetectedByExtensionToo() {
        String text = ocr.extractText("receipt.TXT", "application/octet-stream", stream("TOTAL   1.00"));

        assertThat(text).isEqualTo("TOTAL   1.00");
    }

    @Test
    void byteOrderMarkIsRemoved() {
        String text = ocr.extractText("a.txt", "text/plain", stream("\uFEFFMERCHANT: X"));

        assertThat(text).isEqualTo("MERCHANT: X");
    }

    @Test
    void imageIsMatchedToFixtureByName() throws IOException {
        String text = ocr.extractText("receipt-clean.png", "image/png", stream("not really a png"));

        assertThat(text).isEqualTo(Files.readString(FIXTURES.resolve("receipt-clean.txt")));
    }

    @Test
    void pdfIsMatchedToFixtureByName() throws IOException {
        String text = ocr.extractText("receipt-mismatch.pdf", "application/pdf", stream("%PDF"));

        assertThat(text).isEqualTo(Files.readString(FIXTURES.resolve("receipt-mismatch.txt")));
    }

    @Test
    void unknownFileGivesEmptyText() {
        assertThat(ocr.extractText("holiday-photo.jpg", "image/jpeg", stream("jpg"))).isEmpty();
    }

    @Test
    void pathInFilenameCannotReachOutsideFixtures() {
        assertThat(ocr.extractText("../../pom.png", "image/png", stream("x"))).isEmpty();
        assertThat(ocr.extractText("..", "image/png", stream("x"))).isEmpty();
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
