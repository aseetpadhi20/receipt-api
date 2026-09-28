package com.receipts.receipt;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.receipts.expense.api.TransactionResponse;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptResponse upload(@RequestParam(required = false) MultipartFile file) {
        return receiptService.upload(file);
    }

    @PostMapping("/{receiptId}/process")
    public TransactionResponse process(@PathVariable Long receiptId) {
        return receiptService.process(receiptId);
    }
}
