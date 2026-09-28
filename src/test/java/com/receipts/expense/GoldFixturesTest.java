package com.receipts.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.receipts.expense.domain.ItemizeStatus;
import com.receipts.expense.parse.ParsedReceipt;
import com.receipts.expense.parse.ParsedReceipt.ParsedItem;
import com.receipts.expense.parse.ParsedReceipt.ParsedTax;
import com.receipts.expense.parse.ReceiptParser;
import com.receipts.expense.reconcile.ReconcileResult;
import com.receipts.expense.reconcile.Reconciler;
import com.receipts.expense.reconcile.Reconciler.TaxAmount;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GoldFixturesTest {

    private static final Path FIXTURES = Path.of("fixtures", "task-a");

    private static JsonNode gold;

    private final ReceiptParser parser = new ReceiptParser();
    private final Reconciler reconciler = new Reconciler();

    @BeforeAll
    static void loadGold() throws IOException {
        gold = new ObjectMapper().readTree(Files.readString(FIXTURES.resolve("gold.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"receipt-clean", "receipt-tax-only", "receipt-mismatch"})
    void matchesGold(String fixture) throws IOException {
        JsonNode expected = gold.get(fixture);
        ParsedReceipt parsed = parser.parse(Files.readString(FIXTURES.resolve(fixture + ".txt")));

        assertThat(parsed.merchant()).isEqualTo(expected.get("merchant").asString());
        assertThat(parsed.date()).isEqualTo(LocalDate.parse(expected.get("date").asString()));
        assertThat(parsed.currency()).isEqualTo(expected.get("currency").asString());
        assertThat(parsed.grandTotal()).isEqualByComparingTo(expected.get("grand_total").decimalValue());

        JsonNode goldTaxes = expected.get("taxes");
        assertThat(parsed.taxes()).hasSize(goldTaxes.size());
        for (int i = 0; i < goldTaxes.size(); i++) {
            ParsedTax tax = parsed.taxes().get(i);
            JsonNode goldTax = goldTaxes.get(i);
            assertThat(tax.name()).isEqualTo(goldTax.get("name").asString());
            assertThat(tax.rate()).isEqualByComparingTo(goldTax.get("rate").decimalValue());
            assertThat(tax.amount()).isEqualByComparingTo(goldTax.get("amount").decimalValue());
        }

        JsonNode goldItems = expected.get("line_items");
        assertThat(parsed.items()).hasSize(goldItems.size());
        for (int i = 0; i < goldItems.size(); i++) {
            ParsedItem item = parsed.items().get(i);
            JsonNode goldItem = goldItems.get(i);
            assertThat(item.description()).isEqualTo(goldItem.get("description").asString());
            assertThat(item.amount()).isEqualByComparingTo(goldItem.get("amount").decimalValue());
        }

        ReconcileResult result = reconcile(parsed);
        assertThat(result.status()).isEqualTo(ItemizeStatus.valueOf(expected.get("itemize_status").asString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"receipt-clean", "receipt-tax-only", "receipt-mismatch"})
    void grandTotalIsKeptAsPrinted(String fixture) throws IOException {
        ParsedReceipt parsed = parser.parse(Files.readString(FIXTURES.resolve(fixture + ".txt")));

        ReconcileResult result = reconcile(parsed);

        assertThat(result.grandTotal()).isEqualByComparingTo(gold.get(fixture).get("grand_total").decimalValue());
    }

    private ReconcileResult reconcile(ParsedReceipt parsed) {
        List<TaxAmount> taxes = parsed.taxes().stream()
                .map(tax -> new TaxAmount(tax.amount(), tax.inclusive()))
                .toList();
        List<BigDecimal> amounts = parsed.items().stream().map(ParsedItem::amount).toList();
        return reconciler.check(parsed.grandTotal(), taxes, amounts);
    }
}
