package com.receipts.expense.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.receipts.expense.domain.ExpenseTransaction;
import com.receipts.expense.domain.ItemSource;
import com.receipts.expense.domain.ItemizeStatus;
import com.receipts.expense.domain.TransactionItem;
import com.receipts.expense.domain.TransactionTax;

public record TransactionResponse(
        Long id,
        Long receiptId,
        String merchant,
        LocalDate date,
        String currency,
        BigDecimal grandTotal,
        List<TaxResponse> taxes,
        List<LineItemResponse> lineItems,
        ItemizeStatus itemizeStatus,
        String itemizeNote,
        long version) {

    public record TaxResponse(String name, BigDecimal rate, BigDecimal amount, boolean inclusive) {

        static TaxResponse from(TransactionTax tax) {
            return new TaxResponse(tax.getName(), tax.getRate().stripTrailingZeros(), tax.getAmount(), tax.isInclusive());
        }
    }

    public record LineItemResponse(String description, BigDecimal amount, BigDecimal quantity,
                                   BigDecimal taxAmount, ItemSource source) {

        static LineItemResponse from(TransactionItem item) {
            return new LineItemResponse(item.getDescription(), item.getAmount(), item.getQuantity(),
                    item.getTaxAmount(), item.getSource());
        }
    }

    public static TransactionResponse from(ExpenseTransaction txn) {
        return new TransactionResponse(
                txn.getId(),
                txn.getReceiptId(),
                txn.getMerchant(),
                txn.getTransactionDate(),
                txn.getCurrency(),
                txn.getGrandTotal(),
                txn.getTaxes().stream().map(TaxResponse::from).toList(),
                txn.getItems().stream().map(LineItemResponse::from).toList(),
                txn.getItemizeStatus(),
                txn.getItemizeNote(),
                txn.getVersion());
    }
}
