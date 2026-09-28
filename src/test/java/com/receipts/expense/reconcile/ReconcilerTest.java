package com.receipts.expense.reconcile;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.receipts.expense.domain.ItemizeStatus;
import com.receipts.expense.reconcile.Reconciler.TaxAmount;

class ReconcilerTest {

    private final Reconciler reconciler = new Reconciler();

    @Test
    void noGrandTotalIsFailed() {
        ReconcileResult result = reconciler.check(null, List.of(), amounts("3.50"));

        assertThat(result.status()).isEqualTo(ItemizeStatus.FAILED);
        assertThat(result.expectedItemsTotal()).isNull();
    }

    @Test
    void noItemsNeedsReview() {
        ReconcileResult result = reconciler.check(dec("24.00"), List.of(inclusive("3.83")), List.of());

        assertThat(result.status()).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
        assertThat(result.note()).isEqualTo("no line items found");
    }

    @Test
    void zeroReceiptWithNoItemsIsNotComplete() {
        ReconcileResult result = reconciler.check(dec("0.00"), List.of(), List.of());

        assertThat(result.status()).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
    }

    @Test
    void exclusiveTaxIsSubtracted() {
        ReconcileResult result = reconciler.check(dec("17.85"), List.of(exclusive("2.85")), amounts("3.50", "8.90", "2.60"));

        assertThat(result.status()).isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(result.expectedItemsTotal()).isEqualByComparingTo("15.00");
        assertThat(result.note()).isNull();
    }

    @Test
    void inclusiveTaxIsNotSubtracted() {
        ReconcileResult result = reconciler.check(dec("24.00"), List.of(inclusive("3.83")), amounts("24.00"));

        assertThat(result.status()).isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(result.expectedItemsTotal()).isEqualByComparingTo("24.00");
    }

    @Test
    void mixedTaxesSubtractOnlyExclusive() {
        ReconcileResult result = reconciler.check(dec("12.00"),
                List.of(exclusive("1.00"), inclusive("0.50")), amounts("11.00"));

        assertThat(result.status()).isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(result.exclusiveTaxes()).isEqualByComparingTo("1.00");
    }

    @Test
    void differenceOfExactlyOneCentIsComplete() {
        ReconcileResult result = reconciler.check(dec("10.00"), List.of(), amounts("10.01"));

        assertThat(result.status()).isEqualTo(ItemizeStatus.COMPLETE);
    }

    @Test
    void differenceOverOneCentNeedsReview() {
        ReconcileResult result = reconciler.check(dec("10.00"), List.of(), amounts("10.02"));

        assertThat(result.status()).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
    }

    @Test
    void mismatchReportsNumbers() {
        ReconcileResult result = reconciler.check(dec("18.50"), List.of(exclusive("1.90")), amounts("4.00", "6.00"));

        assertThat(result.status()).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
        assertThat(result.grandTotal()).isEqualByComparingTo("18.50");
        assertThat(result.exclusiveTaxes()).isEqualByComparingTo("1.90");
        assertThat(result.expectedItemsTotal()).isEqualByComparingTo("16.60");
        assertThat(result.actualItemsTotal()).isEqualByComparingTo("10.00");
        assertThat(result.difference()).isEqualByComparingTo("-6.60");
        assertThat(result.note()).isEqualTo("items 10.00, expected 16.60, difference -6.60");
    }

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }

    private static List<BigDecimal> amounts(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

    private static TaxAmount exclusive(String amount) {
        return new TaxAmount(dec(amount), false);
    }

    private static TaxAmount inclusive(String amount) {
        return new TaxAmount(dec(amount), true);
    }
}
