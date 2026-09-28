package com.receipts.receipt;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.receipts.expense.OcrTextSource;

@Component
class ReceiptOcrTextSource implements OcrTextSource {

    private final ReceiptRepository receipts;

    ReceiptOcrTextSource(ReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Override
    public Optional<String> rawOcrText(Long receiptId) {
        return receipts.findById(receiptId).map(Receipt::getRawOcrText);
    }
}
