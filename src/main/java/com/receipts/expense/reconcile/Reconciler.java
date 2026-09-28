package com.receipts.expense.reconcile;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.receipts.expense.domain.ItemizeStatus;

@Component
public class Reconciler {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    public record TaxAmount(BigDecimal amount, boolean inclusive) {
    }

    public ReconcileResult check(BigDecimal grandTotal, List<TaxAmount> taxes, List<BigDecimal> itemAmounts) {
        if (grandTotal == null) {
            return new ReconcileResult(ItemizeStatus.FAILED, "grand total not found",
                    null, null, null, null, null);
        }

        BigDecimal exclusiveTaxes = taxes.stream()
                .filter(tax -> !tax.inclusive())
                .map(TaxAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = grandTotal.subtract(exclusiveTaxes);
        BigDecimal actual = itemAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = actual.subtract(expected);

        if (itemAmounts.isEmpty()) {
            return new ReconcileResult(ItemizeStatus.NEEDS_REVIEW, "no line items found",
                    grandTotal, exclusiveTaxes, expected, actual, difference);
        }

        if (difference.abs().compareTo(TOLERANCE) > 0) {
            String note = "items %s, expected %s, difference %s"
                    .formatted(actual.toPlainString(), expected.toPlainString(), difference.toPlainString());
            return new ReconcileResult(ItemizeStatus.NEEDS_REVIEW, note,
                    grandTotal, exclusiveTaxes, expected, actual, difference);
        }

        return new ReconcileResult(ItemizeStatus.COMPLETE, null,
                grandTotal, exclusiveTaxes, expected, actual, difference);
    }
}
