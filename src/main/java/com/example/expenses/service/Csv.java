package com.example.expenses.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny CSV helpers (RFC 4180 style) so we don't need a CSV library.
 */
public final class Csv {

    private static final char BOM = 0xFEFF;
    private static final String FORMULA_START = "=+-@\t\r";

    private Csv() {
    }

    /**
     * Quotes a value when it contains a comma, quote or line break, doubling any quotes inside.
     * Values starting with = + - @ are prefixed with ' so spreadsheet apps don't run them
     * as formulas (CSV injection).
     */
    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = startsLikeFormula(value) ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    /** Undoes the ' prefix that {@link #escape} adds, so an exported file imports back unchanged. */
    public static String unescapeFormula(String value) {
        if (value.length() > 1 && value.charAt(0) == '\'' && startsLikeFormula(value.substring(1))) {
            return value.substring(1);
        }
        return value;
    }

    /**
     * Splits CSV text into rows of fields. Handles quoted fields with commas, doubled quotes and
     * line breaks, both CRLF and LF line endings, and a UTF-8 byte order mark. Blank lines are skipped.
     */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int start = !text.isEmpty() && text.charAt(0) == BOM ? 1 : 0;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                endRow(rows, row, field);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        endRow(rows, row, field);
        return rows;
    }

    private static void endRow(List<List<String>> rows, List<String> row, StringBuilder field) {
        row.add(field.toString());
        field.setLength(0);
        boolean blank = row.stream().allMatch(String::isBlank);
        if (!blank) {
            rows.add(row);
        }
    }

    static boolean startsLikeFormula(String value) {
        return !value.isEmpty() && FORMULA_START.indexOf(value.charAt(0)) >= 0;
    }
}
