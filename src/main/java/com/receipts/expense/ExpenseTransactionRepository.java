package com.receipts.expense;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.receipts.expense.domain.ExpenseTransaction;

public interface ExpenseTransactionRepository extends JpaRepository<ExpenseTransaction, Long> {

    Optional<ExpenseTransaction> findByReceiptId(Long receiptId);
}
