package com.receipts.expense.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ParsedReceipt(
        String merchant,
        LocalDate date,
        String currency,
        BigDecimal grandTotal,
        List<ParsedTax> taxes,
        List<ParsedItem> items) {

    public record ParsedTax(String name, BigDecimal rate, BigDecimal amount, boolean inclusive) {
    }

    public record ParsedItem(String description, BigDecimal amount) {
    }
}
