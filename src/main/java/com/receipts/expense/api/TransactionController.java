package com.receipts.expense.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.receipts.expense.NewItem;
import com.receipts.expense.TransactionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/transactions/{transactionId}")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public TransactionResponse get(@PathVariable Long transactionId) {
        return transactionService.get(transactionId);
    }

    @PostMapping("/itemize")
    public TransactionResponse reitemize(@PathVariable Long transactionId) {
        return transactionService.reitemize(transactionId);
    }

    @PatchMapping("/items")
    public TransactionResponse replaceItems(@PathVariable Long transactionId, @Valid @RequestBody PatchItemsRequest request) {
        var items = request.items().stream()
                .map(item -> new NewItem(item.description().strip(), item.amount(), item.quantity(), item.taxAmount()))
                .toList();
        return transactionService.replaceItems(transactionId, items, request.version());
    }
}
