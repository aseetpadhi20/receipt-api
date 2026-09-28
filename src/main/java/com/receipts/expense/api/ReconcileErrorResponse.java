package com.receipts.expense.api;

import java.math.BigDecimal;

import com.receipts.expense.reconcile.ReconcileResult;

public record ReconcileErrorResponse(
        String error,
        String message,
        BigDecimal grandTotal,
        BigDecimal exclusiveTaxes,
        BigDecimal expectedItemsTotal,
        BigDecimal actualItemsTotal,
        BigDecimal difference) {

    public static ReconcileErrorResponse of(String error, String message, ReconcileResult result) {
        return new ReconcileErrorResponse(error, message, result.grandTotal(), result.exclusiveTaxes(),
                result.expectedItemsTotal(), result.actualItemsTotal(), result.difference());
    }
}
