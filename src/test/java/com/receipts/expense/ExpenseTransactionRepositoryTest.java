package com.receipts.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.receipts.expense.domain.ExpenseTransaction;
import com.receipts.expense.domain.ItemSource;
import com.receipts.expense.domain.ItemizeStatus;
import com.receipts.expense.domain.TransactionItem;
import com.receipts.expense.domain.TransactionTax;
import com.receipts.receipt.Receipt;
import com.receipts.receipt.ReceiptRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class ExpenseTransactionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-03-12T10:00:00Z");

    @Autowired
    private ReceiptRepository receipts;

    @Autowired
    private ExpenseTransactionRepository transactions;

    @Autowired
    private EntityManager em;

    @Test
    void savesAndLoadsTransactionWithTaxesAndItems() {
        Receipt receipt = saveReceipt("a");
        ExpenseTransaction txn = cleanTransaction(receipt.getId());
        transactions.saveAndFlush(txn);
        em.clear();

        ExpenseTransaction loaded = transactions.findByReceiptId(receipt.getId()).orElseThrow();

        assertThat(loaded.getMerchant()).isEqualTo("Cafe Mitte");
        assertThat(loaded.getTransactionDate()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(loaded.getCurrency()).isEqualTo("EUR");
        assertThat(loaded.getGrandTotal()).isEqualByComparingTo("17.85");
        assertThat(loaded.getItemizeStatus()).isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getTaxes()).singleElement().satisfies(tax -> {
            assertThat(tax.getName()).isEqualTo("VAT");
            assertThat(tax.getRate()).isEqualByComparingTo("0.19");
            assertThat(tax.isInclusive()).isFalse();
        });
        assertThat(loaded.getItems()).extracting(TransactionItem::getDescription)
                .containsExactly("Espresso", "Sandwich", "Mineral water");
        assertThat(loaded.getItems()).extracting(TransactionItem::getPosition).containsExactly(1, 2, 3);
    }

    @Test
    void replacingItemsIncreasesVersion() {
        Receipt receipt = saveReceipt("b");
        ExpenseTransaction txn = transactions.saveAndFlush(cleanTransaction(receipt.getId()));
        assertThat(txn.getVersion()).isZero();

        txn.replaceItems(List.of(item("Espresso", "15.00")), NOW.plusSeconds(1));
        transactions.saveAndFlush(txn);
        em.clear();

        ExpenseTransaction loaded = transactions.findById(txn.getId()).orElseThrow();
        assertThat(loaded.getVersion()).isEqualTo(1);
        assertThat(loaded.getItems()).singleElement()
                .satisfies(i -> assertThat(i.getDescription()).isEqualTo("Espresso"));
    }

    @Test
    void onlyOneTransactionPerReceipt() {
        Receipt receipt = saveReceipt("c");
        transactions.saveAndFlush(cleanTransaction(receipt.getId()));

        assertThatThrownBy(() -> transactions.saveAndFlush(cleanTransaction(receipt.getId())))
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class);
    }

    @Test
    void totalCanOnlyBeNullWhenFailed() {
        Receipt receipt = saveReceipt("d");
        ExpenseTransaction txn = new ExpenseTransaction(receipt.getId(), NOW);
        txn.setHeader(null, null, null, null, NOW);
        txn.setItemizeResult(ItemizeStatus.COMPLETE, null, NOW);

        assertThatThrownBy(() -> transactions.saveAndFlush(txn))
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class);
    }

    @Test
    void deletingReceiptCascadesToTransactionTaxesAndItems() {
        Receipt receipt = saveReceipt("e");
        ExpenseTransaction txn = transactions.saveAndFlush(cleanTransaction(receipt.getId()));
        em.clear();

        receipts.deleteById(receipt.getId());
        receipts.flush();
        em.clear();

        assertThat(transactions.findById(txn.getId())).isEmpty();
        assertThat(count("transaction_tax", txn.getId())).isZero();
        assertThat(count("transaction_item", txn.getId())).isZero();
    }

    private Receipt saveReceipt(String key) {
        return receipts.saveAndFlush(new Receipt("receipt-clean.txt", "text/plain", 212, "local", "receipts/test/" + key, NOW));
    }

    private static ExpenseTransaction cleanTransaction(Long receiptId) {
        ExpenseTransaction txn = new ExpenseTransaction(receiptId, NOW);
        txn.setHeader("Cafe Mitte", LocalDate.of(2026, 3, 12), "EUR", new BigDecimal("17.85"), NOW);
        txn.replaceTaxes(List.of(new TransactionTax("VAT", new BigDecimal("0.1900"), new BigDecimal("2.85"), false, null)), NOW);
        txn.replaceItems(List.of(item("Espresso", "3.50"), item("Sandwich", "8.90"), item("Mineral water", "2.60")), NOW);
        txn.setItemizeResult(ItemizeStatus.COMPLETE, null, NOW);
        return txn;
    }

    private static TransactionItem item(String description, String amount) {
        return new TransactionItem(description, new BigDecimal(amount), null, null, ItemSource.AUTO);
    }

    private long count(String table, Long transactionId) {
        return ((Number) em.createNativeQuery("select count(*) from expenses." + table + " where transaction_id = :id")
                .setParameter("id", transactionId)
                .getSingleResult()).longValue();
    }
}
