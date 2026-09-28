package com.receipts.receipt;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "receipt")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_backend", nullable = false, length = 20)
    private String storageBackend;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "raw_ocr_text")
    private String rawOcrText;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Receipt() {
    }

    public Receipt(String originalFilename, String contentType, long sizeBytes,
                   String storageBackend, String storageKey, Instant uploadedAt) {
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageBackend = storageBackend;
        this.storageKey = storageKey;
        this.uploadedAt = uploadedAt;
    }

    public void markProcessed(String rawOcrText, Instant processedAt) {
        this.rawOcrText = rawOcrText;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getRawOcrText() {
        return rawOcrText;
    }
}
