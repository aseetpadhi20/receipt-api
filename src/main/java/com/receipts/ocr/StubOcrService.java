package com.receipts.ocr;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StubOcrService implements OcrService {

    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private final Path fixturesDir;

    public StubOcrService(@Value("${ocr.stub.fixtures-dir}") Path fixturesDir) {
        this.fixturesDir = fixturesDir.toAbsolutePath().normalize();
    }

    @Override
    public String extractText(String originalFilename, String contentType, InputStream content) {
        try {
            if (isText(originalFilename, contentType)) {
                return stripBom(new String(content.readAllBytes(), StandardCharsets.UTF_8));
            }
            Path fixture = fixtureFor(originalFilename);
            return fixture != null && Files.isRegularFile(fixture)
                    ? stripBom(Files.readString(fixture, StandardCharsets.UTF_8))
                    : "";
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read receipt text", e);
        }
    }

    private static boolean isText(String filename, String contentType) {
        return "text/plain".equals(contentType)
                || (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".txt"));
    }

    private Path fixtureFor(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (!base.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        Path fixture = fixturesDir.resolve(base + ".txt").normalize();
        return fixture.startsWith(fixturesDir) ? fixture : null;
    }

    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    }
}
