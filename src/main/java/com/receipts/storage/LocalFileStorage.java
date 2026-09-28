package com.receipts.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${storage.local.dir}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String backendName() {
        return "local";
    }

    @Override
    public void store(String key, InputStream data, long size, String contentType) {
        Path target = resolve(key);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.copy(data, temp, StandardCopyOption.REPLACE_EXISTING);
            move(temp, target);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new StorageException("Could not store file " + key, e);
        }
    }

    @Override
    public InputStream load(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new StorageFileNotFoundException(key);
        } catch (IOException e) {
            throw new StorageException("Could not read file " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Could not delete file " + key, e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new StorageException("Invalid storage key " + key);
        }
        return path;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort; the temp file name marks it as safe to clean up later
        }
    }
}
