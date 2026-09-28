package com.receipts.expense.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_item")
public class TransactionItem {

    private static final int DESCRIPTION_MAX = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ExpenseTransaction transaction;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "description", nullable = false, length = DESCRIPTION_MAX)
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "quantity", precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source", nullable = false, length = 10)
    private ItemSource source;

    protected TransactionItem() {
    }

    public TransactionItem(String description, BigDecimal amount, BigDecimal quantity, BigDecimal taxAmount, ItemSource source) {
        this.description = truncate(description, DESCRIPTION_MAX);
        this.amount = amount;
        this.quantity = quantity;
        this.taxAmount = taxAmount;
        this.source = source;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    void attachTo(ExpenseTransaction transaction, int position) {
        this.transaction = transaction;
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public ItemSource getSource() {
        return source;
    }
}
