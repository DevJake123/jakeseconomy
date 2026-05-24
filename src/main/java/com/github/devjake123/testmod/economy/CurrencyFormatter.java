package com.github.devjake123.testmod.economy;

public class CurrencyFormatter {

    // Abbreviated suffixes in ascending order
    private static final long[] THRESHOLDS = {
            1_000_000_000_000_000_000L, // quintillion (E)
            1_000_000_000_000_000L,     // quadrillion (P)
            1_000_000_000_000L,         // trillion (T)
            1_000_000_000L,             // billion (B)
            1_000_000L,                 // million (M)
            1_000L                      // thousand (k)
    };

    private static final String[] SUFFIXES = { "E", "P", "T", "B", "M", "k" };

    // Formats a currency value.
    // If abbreviated=true and decimalPlaces is set, returns e.g. "1.23M", "53.14M"
    // Otherwise returns the exact value with commas e.g. "1,230,000"
    public static String format(long value, boolean abbreviated, int decimalPlaces) {
        if (!abbreviated || value < 1_000) {
            return "$" + String.format("%,d", value);
        }
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (value >= THRESHOLDS[i]) {
                double divided = (double) value / THRESHOLDS[i];
                String fmt = "%." + decimalPlaces + "f";
                return "$" + String.format(fmt, divided) + SUFFIXES[i];
            }
        }
        return "$" + String.format("%,d", value);
    }

    // Convenience overload using 2 decimal places (default from client config)
    public static String format(long value, boolean abbreviated) {
        return format(value, abbreviated, 2);
    }

    /**
     * Formats a double price value.
     * Values >= 1000 abbreviate the same as the long overload.
     * Values < 1000 always show 2 decimal places (e.g. $1.00, $1.25, $0.80).
     */
    public static String format(double value, boolean abbreviated) {
        if (value >= 1_000) {
            return format((long) value, abbreviated);
        }
        return "$" + String.format("%,.2f", value);
    }

}
