package com.receipts.expense.parse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.receipts.expense.parse.ParsedReceipt.ParsedItem;
import com.receipts.expense.parse.ParsedReceipt.ParsedTax;

@Component
public class ReceiptParser {

    private static final int RATE_SCALE = 6;

    private static final Pattern MERCHANT = Pattern.compile("^MERCHANT:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("^DATE:\\s*(\\S+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENCY = Pattern.compile("^CURRENCY:\\s*([A-Za-z]{3})$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOTAL = Pattern.compile("^(?:GRAND\\s+)?TOTAL\\s+(\\d+\\.\\d{2})$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TAX = Pattern.compile(
            "^(incl\\.?\\s+)?(VAT|GST|HST|PST|MWST|SALES\\s+TAX|TAX)\\s+(\\d{1,2}(?:\\.\\d{1,4})?)\\s*%\\s+(\\d+\\.\\d{2})$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ITEM = Pattern.compile("^(.+?)\\s{2,}(\\d+\\.\\d{2})$");

    private static final Pattern SUMMARY_LABEL = Pattern.compile(
            "^(SUB\\s*TOTAL|GRAND\\s+TOTAL|TOTAL|BALANCE|CHANGE|CASH|CARD|AMOUNT\\s+DUE)\\s*:?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TAX_LABEL = Pattern.compile(
            "^(incl\\.?\\s+)?(VAT|GST|HST|PST|MWST|SALES\\s+TAX|TAX)(\\s+[\\d.]+\\s*%)?\\s*:?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public ParsedReceipt parse(String text) {
        String merchant = null;
        LocalDate date = null;
        String currency = null;
        BigDecimal grandTotal = null;
        List<ParsedTax> taxes = new ArrayList<>();
        List<ParsedItem> items = new ArrayList<>();

        if (text == null) {
            return new ParsedReceipt(null, null, null, null, taxes, items);
        }

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }

            Matcher m;
            if ((m = MERCHANT.matcher(line)).matches()) {
                merchant = m.group(1).strip();
            } else if ((m = DATE.matcher(line)).matches()) {
                date = parseDate(m.group(1));
            } else if ((m = CURRENCY.matcher(line)).matches()) {
                currency = m.group(1).toUpperCase(Locale.ROOT);
            } else if ((m = TOTAL.matcher(line)).matches()) {
                if (grandTotal == null) {
                    grandTotal = new BigDecimal(m.group(1));
                }
            } else if ((m = TAX.matcher(line)).matches()) {
                taxes.add(new ParsedTax(
                        WHITESPACE.matcher(m.group(2).toUpperCase(Locale.ROOT)).replaceAll(" "),
                        percentToRate(m.group(3)),
                        new BigDecimal(m.group(4)),
                        m.group(1) != null));
            } else if ((m = ITEM.matcher(line)).matches() && isItemLabel(m.group(1).strip())) {
                items.add(new ParsedItem(m.group(1).strip(), new BigDecimal(m.group(2))));
            }
        }

        return new ParsedReceipt(merchant, date, currency, grandTotal, taxes, items);
    }

    private static boolean isItemLabel(String label) {
        return !SUMMARY_LABEL.matcher(label).matches() && !TAX_LABEL.matcher(label).matches();
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException _) {
            return null;
        }
    }

    private static BigDecimal percentToRate(String percent) {
        return new BigDecimal(percent).movePointLeft(2).setScale(RATE_SCALE, RoundingMode.UNNECESSARY);
    }
}
