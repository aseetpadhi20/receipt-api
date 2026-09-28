package com.receipts.expense.api;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PatchItemsRequest(
        Long version,
        @NotEmpty(message = "must contain at least one item") List<@Valid @NotNull ItemInput> items) {

    public record ItemInput(
            @NotBlank @Size(max = 255) String description,

            @NotNull
            @DecimalMin("0.00")
            @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places and 10 digits before the point")
            BigDecimal amount,

            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 7, fraction = 3, message = "must have at most 3 decimal places and 7 digits before the point")
            BigDecimal quantity,

            @DecimalMin("0.00")
            @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places and 10 digits before the point")
            BigDecimal taxAmount) {
    }
}
