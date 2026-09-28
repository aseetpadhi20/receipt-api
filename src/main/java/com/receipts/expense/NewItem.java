package com.receipts.expense;

import java.math.BigDecimal;

public record NewItem(String description, BigDecimal amount, BigDecimal quantity, BigDecimal taxAmount) {
}
