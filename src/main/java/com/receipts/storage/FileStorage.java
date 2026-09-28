package com.receipts.storage;

import java.io.InputStream;

public interface FileStorage {

    String backendName();

    void store(String key, InputStream data, long size, String contentType);

    InputStream load(String key);

    void delete(String key);
}
