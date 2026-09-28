package com.receipts.receipt;

public record ReceiptResponse(Long receiptId, String filename, String contentType, long sizeBytes) {

    static ReceiptResponse from(Receipt receipt) {
        return new ReceiptResponse(receipt.getId(), receipt.getOriginalFilename(), receipt.getContentType(), receipt.getSizeBytes());
    }
}
