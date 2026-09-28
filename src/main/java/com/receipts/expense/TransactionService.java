package com.receipts.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.receipts.common.ApiException;
import com.receipts.expense.api.TransactionResponse;
import com.receipts.expense.domain.ExpenseTransaction;
import com.receipts.expense.domain.ItemSource;
import com.receipts.expense.domain.ItemizeStatus;
import com.receipts.expense.domain.TransactionItem;
import com.receipts.expense.domain.TransactionTax;
import com.receipts.expense.parse.ParsedReceipt;
import com.receipts.expense.parse.ReceiptParser;
import com.receipts.expense.reconcile.ReconcileResult;
import com.receipts.expense.reconcile.Reconciler;
import com.receipts.expense.reconcile.Reconciler.TaxAmount;

@Service
public class TransactionService {

    private final ExpenseTransactionRepository transactions;
    private final OcrTextSource ocrTextSource;
    private final ReceiptParser parser;
    private final Reconciler reconciler;

    public TransactionService(ExpenseTransactionRepository transactions, OcrTextSource ocrTextSource,
                              ReceiptParser parser, Reconciler reconciler) {
        this.transactions = transactions;
        this.ocrTextSource = ocrTextSource;
        this.parser = parser;
        this.reconciler = reconciler;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionResponse createOrUpdate(Long receiptId, String ocrText) {
        Instant now = Instant.now();
        ParsedReceipt parsed = parser.parse(ocrText);
        ExpenseTransaction txn = transactions.findByReceiptId(receiptId)
                .orElseGet(() -> new ExpenseTransaction(receiptId, now));

        txn.setHeader(parsed.merchant(), parsed.date(), parsed.currency(), parsed.grandTotal(), now);
        txn.replaceTaxes(parsed.taxes().stream()
                .map(tax -> new TransactionTax(tax.name(), tax.rate(), tax.amount(), tax.inclusive(), null))
                .toList(), now);
        applyAutoItems(txn, parsed, now);

        return TransactionResponse.from(transactions.saveAndFlush(txn));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long id) {
        return TransactionResponse.from(find(id));
    }

    @Transactional
    public TransactionResponse reitemize(Long id) {
        ExpenseTransaction txn = find(id);
        String text = ocrTextSource.rawOcrText(txn.getReceiptId()).orElse("");

        applyAutoItems(txn, parser.parse(text), Instant.now());

        return TransactionResponse.from(transactions.saveAndFlush(txn));
    }

    @Transactional
    public TransactionResponse replaceItems(Long id, List<NewItem> newItems, Long expectedVersion) {
        ExpenseTransaction txn = find(id);

        if (expectedVersion != null && expectedVersion != txn.getVersion()) {
            throw ApiException.conflict("VERSION_CONFLICT", "Transaction %d was changed (current version %d). Reload and try again"
                    .formatted(id, txn.getVersion()));
        }
        if (txn.getItemizeStatus() == ItemizeStatus.FAILED) {
            throw ApiException.conflict("TRANSACTION_FAILED", "No grand total to reconcile against");
        }

        ReconcileResult result = reconcile(txn, newItems.stream().map(NewItem::amount).toList());
        if (!result.isComplete()) {
            throw new ItemsDoNotReconcileException(result);
        }

        Instant now = Instant.now();
        txn.replaceItems(newItems.stream()
                .map(item -> new TransactionItem(item.description(), item.amount(), item.quantity(), item.taxAmount(), ItemSource.USER))
                .toList(), now);
        txn.setItemizeResult(ItemizeStatus.COMPLETE, null, now);

        return TransactionResponse.from(transactions.saveAndFlush(txn));
    }

    private void applyAutoItems(ExpenseTransaction txn, ParsedReceipt parsed, Instant now) {
        txn.replaceItems(parsed.items().stream()
                .map(item -> new TransactionItem(item.description(), item.amount(), null, null, ItemSource.AUTO))
                .toList(), now);
        ReconcileResult result = reconcile(txn, parsed.items().stream().map(ParsedReceipt.ParsedItem::amount).toList());
        txn.setItemizeResult(result.status(), result.note(), now);
    }

    private ReconcileResult reconcile(ExpenseTransaction txn, List<BigDecimal> itemAmounts) {
        List<TaxAmount> taxes = txn.getTaxes().stream()
                .map(tax -> new TaxAmount(tax.getAmount(), tax.isInclusive()))
                .toList();
        return reconciler.check(txn.getGrandTotal(), taxes, itemAmounts);
    }

    private ExpenseTransaction find(Long id) {
        return transactions.findById(id).orElseThrow(() -> ApiException.notFound("Transaction", id));
    }
}
