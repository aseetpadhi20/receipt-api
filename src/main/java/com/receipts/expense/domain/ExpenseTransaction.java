package com.receipts.expense.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "expense_transaction")
public class ExpenseTransaction {

    private static final int MERCHANT_MAX = 255;
    private static final int NOTE_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_id", nullable = false, unique = true, updatable = false)
    private Long receiptId;

    @Column(name = "merchant", length = MERCHANT_MAX)
    private String merchant;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "grand_total", precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "itemize_status", nullable = false, length = 20)
    private ItemizeStatus itemizeStatus;

    @Column(name = "itemize_note", length = NOTE_MAX)
    private String itemizeNote;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<TransactionTax> taxes = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<TransactionItem> items = new ArrayList<>();

    protected ExpenseTransaction() {
    }

    public ExpenseTransaction(Long receiptId, Instant now) {
        this.receiptId = receiptId;
        this.itemizeStatus = ItemizeStatus.FAILED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void setHeader(String merchant, LocalDate transactionDate, String currency, BigDecimal grandTotal, Instant now) {
        this.merchant = truncate(merchant, MERCHANT_MAX);
        this.transactionDate = transactionDate;
        this.currency = currency;
        this.grandTotal = grandTotal;
        this.updatedAt = now;
    }

    public void replaceTaxes(List<TransactionTax> newTaxes, Instant now) {
        taxes.clear();
        for (TransactionTax tax : newTaxes) {
            tax.attachTo(this);
            taxes.add(tax);
        }
        this.updatedAt = now;
    }

    public void replaceItems(List<TransactionItem> newItems, Instant now) {
        items.clear();
        int position = 1;
        for (TransactionItem item : newItems) {
            item.attachTo(this, position++);
            items.add(item);
        }
        this.updatedAt = now;
    }

    public void setItemizeResult(ItemizeStatus status, String note, Instant now) {
        this.itemizeStatus = status;
        this.itemizeNote = truncate(note, NOTE_MAX);
        this.updatedAt = now;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public String getMerchant() {
        return merchant;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public ItemizeStatus getItemizeStatus() {
        return itemizeStatus;
    }

    public String getItemizeNote() {
        return itemizeNote;
    }

    public long getVersion() {
        return version;
    }

    public List<TransactionTax> getTaxes() {
        return Collections.unmodifiableList(taxes);
    }

    public List<TransactionItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
