package com.receipts.expense.reconcile;

import java.math.BigDecimal;

import com.receipts.expense.domain.ItemizeStatus;

public record ReconcileResult(
        ItemizeStatus status,
        String note,
        BigDecimal grandTotal,
        BigDecimal exclusiveTaxes,
        BigDecimal expectedItemsTotal,
        BigDecimal actualItemsTotal,
        BigDecimal difference) {

    public boolean isComplete() {
        return status == ItemizeStatus.COMPLETE;
    }
}
