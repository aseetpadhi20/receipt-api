package com.receipts.storage;

public class StorageFileNotFoundException extends StorageException {

    public StorageFileNotFoundException(String key) {
        super("No file in storage for key " + key);
    }
}
