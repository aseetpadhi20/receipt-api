package com.receipts.expense.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_tax")
public class TransactionTax {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ExpenseTransaction transaction;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "rate", nullable = false, precision = 7, scale = 6)
    private BigDecimal rate;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "inclusive", nullable = false)
    private boolean inclusive;

    @Column(name = "jurisdiction", length = 50)
    private String jurisdiction;

    protected TransactionTax() {
    }

    public TransactionTax(String name, BigDecimal rate, BigDecimal amount, boolean inclusive, String jurisdiction) {
        this.name = name;
        this.rate = rate;
        this.amount = amount;
        this.inclusive = inclusive;
        this.jurisdiction = jurisdiction;
    }

    void attachTo(ExpenseTransaction transaction) {
        this.transaction = transaction;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isInclusive() {
        return inclusive;
    }
}
