package com.example.expenses.service;

/**
 * Tiny CSV helpers (RFC 4180 style) so we don't need a CSV library.
 */
public final class Csv {

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

    static boolean startsLikeFormula(String value) {
        return !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0;
    }
}
