package com.receipts.receipt;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.receipts.common.ApiException;
import com.receipts.expense.TransactionService;
import com.receipts.expense.api.TransactionResponse;
import com.receipts.ocr.OcrService;
import com.receipts.storage.FileStorage;
import com.receipts.storage.StorageException;
import com.receipts.storage.StorageFileNotFoundException;
import com.receipts.storage.StorageKeys;

@Service
public class ReceiptService {

    private static final int FILENAME_MAX = 255;

    private static final Map<String, String> TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf",
            "txt", "text/plain");

    private static final Set<String> ALLOWED_TYPES = Set.copyOf(TYPE_BY_EXTENSION.values());

    private final ReceiptRepository receipts;
    private final FileStorage storage;
    private final OcrService ocr;
    private final TransactionService transactionService;
    private final TransactionTemplate transactionTemplate;

    public ReceiptService(ReceiptRepository receipts, FileStorage storage, OcrService ocr,
                          TransactionService transactionService, TransactionTemplate transactionTemplate) {
        this.receipts = receipts;
        this.storage = storage;
        this.ocr = ocr;
        this.transactionService = transactionService;
        this.transactionTemplate = transactionTemplate;
    }

    public ReceiptResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is missing or empty");
        }
        String original = file.getOriginalFilename();
        String filename = original == null || original.isBlank() ? "file" : original;
        String contentType = contentType(filename, file.getContentType());
        Instant now = Instant.now();
        String key = StorageKeys.newKey(filename, now);

        try (InputStream in = file.getInputStream()) {
            storage.store(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new StorageException("Could not read uploaded file", e);
        }

        try {
            Receipt receipt = receipts.saveAndFlush(new Receipt(truncate(filename, FILENAME_MAX), contentType, file.getSize(),
                    storage.backendName(), key, now));
            return ReceiptResponse.from(receipt);
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
    }

    public TransactionResponse process(Long receiptId) {
        Receipt receipt = receipts.findById(receiptId).orElseThrow(() -> ApiException.notFound("Receipt", receiptId));
        String text = extractText(receipt);

        return transactionTemplate.execute(status -> {
            Receipt locked = receipts.findByIdForUpdate(receiptId)
                    .orElseThrow(() -> ApiException.notFound("Receipt", receiptId));
            locked.markProcessed(text, Instant.now());
            return transactionService.createOrUpdate(receiptId, text);
        });
    }

    private String extractText(Receipt receipt) {
        try (InputStream in = storage.load(receipt.getStorageKey())) {
            String text = ocr.extractText(receipt.getOriginalFilename(), receipt.getContentType(), in);
            return text == null ? "" : text;
        } catch (StorageFileNotFoundException _) {
            throw ApiException.conflict("RECEIPT_FILE_MISSING",
                    "File for receipt " + receipt.getId() + " not found in storage");
        } catch (IOException e) {
            throw new StorageException("Could not read file for receipt " + receipt.getId(), e);
        }
    }

    private static String contentType(String filename, String declared) {
        int dot = filename.lastIndexOf('.');
        String byExtension = dot >= 0 ? TYPE_BY_EXTENSION.get(filename.substring(dot + 1).toLowerCase(Locale.ROOT)) : null;
        if (byExtension != null) {
            return byExtension;
        }
        String type = declared == null ? "" : declared.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (ALLOWED_TYPES.contains(type)) {
            return type;
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Only jpeg, png, pdf and txt are allowed");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
