package com.receipts.expense.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.receipts.expense.parse.ParsedReceipt.ParsedItem;
import com.receipts.expense.parse.ParsedReceipt.ParsedTax;

class ReceiptParserTest {

    private final ReceiptParser parser = new ReceiptParser();

    @Test
    void exclusiveTaxIsNotInclusive() {
        ParsedReceipt parsed = parser.parse("""
                VAT 19%                     2.85
                TOTAL                      17.85
                """);

        assertThat(parsed.taxes()).containsExactly(
                new ParsedTax("VAT", new BigDecimal("0.190000"), new BigDecimal("2.85"), false));
    }

    @Test
    void inclTaxIsInclusive() {
        ParsedReceipt parsed = parser.parse("""
                TOTAL                      24.00
                incl. VAT 19%               3.83
                """);

        assertThat(parsed.taxes()).containsExactly(
                new ParsedTax("VAT", new BigDecimal("0.190000"), new BigDecimal("3.83"), true));
    }

    @Test
    void readsSeveralTaxLines() {
        ParsedReceipt parsed = parser.parse("""
                VAT 7%                      0.59
                VAT 19%                     1.52
                """);

        assertThat(parsed.taxes()).extracting(ParsedTax::rate)
                .containsExactly(new BigDecimal("0.070000"), new BigDecimal("0.190000"));
    }

    @Test
    void readsTaxRateWithMoreThanTwoDecimals() {
        ParsedReceipt parsed = parser.parse("SALES TAX 8.875%            1.33");

        assertThat(parsed.taxes()).containsExactly(
                new ParsedTax("SALES TAX", new BigDecimal("0.088750"), new BigDecimal("1.33"), false));
        assertThat(parsed.items()).isEmpty();
    }

    @Test
    void itemsStartingWithTotalOrTaxWordsAreKept() {
        ParsedReceipt parsed = parser.parse("""
                Tax-free shopping bag       0.50
                Card game                  12.00
                Change purse                8.00
                """);

        assertThat(parsed.items()).extracting(ParsedItem::description)
                .containsExactly("Tax-free shopping bag", "Card game", "Change purse");
    }

    @Test
    void summaryPaymentAndTaxLabelsAreNotItems() {
        ParsedReceipt parsed = parser.parse("""
                Subtotal                   15.00
                Sub total:                 15.00
                Balance                    15.00
                Amount due                 15.00
                Cash                       20.00
                Card                       15.00
                Change                      5.00
                VAT                         2.85
                incl. VAT                   3.83
                """);

        assertThat(parsed.items()).isEmpty();
    }

    @Test
    void subtotalTotalAndTaxLinesAreNotItems() {
        ParsedReceipt parsed = parser.parse("""
                Espresso                    3.50
                Subtotal                    3.50
                VAT 19%                     0.67
                TOTAL                       4.17
                """);

        assertThat(parsed.items()).containsExactly(new ParsedItem("Espresso", new BigDecimal("3.50")));
        assertThat(parsed.grandTotal()).isEqualByComparingTo("4.17");
    }

    @Test
    void lineWithoutPriceIsNotAnItem() {
        ParsedReceipt parsed = parser.parse("""
                Trip fare
                (No itemized list)
                TOTAL                      24.00
                """);

        assertThat(parsed.items()).isEmpty();
    }

    @Test
    void grandTotalLineIsReadAsTotal() {
        ParsedReceipt parsed = parser.parse("GRAND TOTAL                12.00");

        assertThat(parsed.grandTotal()).isEqualByComparingTo("12.00");
        assertThat(parsed.items()).isEmpty();
    }

    @Test
    void missingFieldsAreNull() {
        ParsedReceipt parsed = parser.parse("Espresso                    3.50");

        assertThat(parsed.merchant()).isNull();
        assertThat(parsed.date()).isNull();
        assertThat(parsed.currency()).isNull();
        assertThat(parsed.grandTotal()).isNull();
    }

    @Test
    void invalidDateIsNull() {
        assertThat(parser.parse("DATE: 2026-13-45").date()).isNull();
    }

    @Test
    void emptyOrNullTextGivesEmptyResult() {
        for (String text : new String[] {"", null}) {
            ParsedReceipt parsed = parser.parse(text);
            assertThat(parsed.grandTotal()).isNull();
            assertThat(parsed.taxes()).isEmpty();
            assertThat(parsed.items()).isEmpty();
        }
    }

    @Test
    void handlesWindowsLineEndings() {
        ParsedReceipt parsed = parser.parse("MERCHANT: Cafe Mitte\r\nEspresso                    3.50\r\nTOTAL   3.50\r\n");

        assertThat(parsed.merchant()).isEqualTo("Cafe Mitte");
        assertThat(parsed.items()).hasSize(1);
        assertThat(parsed.grandTotal()).isEqualByComparingTo("3.50");
    }
}
