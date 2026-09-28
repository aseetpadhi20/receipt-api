package com.receipts.storage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

public final class StorageKeys {

    private static final int FILENAME_MAX = 100;

    private StorageKeys() {
    }

    public static String newKey(String originalFilename, Instant now) {
        ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
        return "receipts/%04d/%02d/%s-%s".formatted(
                utc.getYear(), utc.getMonthValue(), UUID.randomUUID(), cleanFilename(originalFilename));
    }

    public static String cleanFilename(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "");
        if (name.length() > FILENAME_MAX) {
            name = name.substring(name.length() - FILENAME_MAX);
        }
        return name.isEmpty() ? "file" : name;
    }
}
