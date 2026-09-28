package com.receipts.expense;

import org.springframework.http.HttpStatus;

import com.receipts.common.ApiException;
import com.receipts.expense.api.ReconcileErrorResponse;
import com.receipts.expense.reconcile.ReconcileResult;

public class ItemsDoNotReconcileException extends ApiException {

    private final transient ReconcileResult result;

    public ItemsDoNotReconcileException(ReconcileResult result) {
        super(HttpStatus.CONFLICT, "ITEMS_DO_NOT_RECONCILE", "Line items do not match the receipt total");
        this.result = result;
    }

    @Override
    public Object body() {
        return ReconcileErrorResponse.of(getCode(), getMessage(), result);
    }
}
