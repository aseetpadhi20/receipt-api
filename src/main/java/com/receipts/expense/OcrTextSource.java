package com.receipts.expense;

import java.util.Optional;

public interface OcrTextSource {

    Optional<String> rawOcrText(Long receiptId);
}
