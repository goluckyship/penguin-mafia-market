package net.penguinmafia.market;

import java.util.Locale;

/**
 * Formats large coin counts with k/m/b/t suffixes (1,200 -> "1.2k",
 * 3,400,000 -> "3.4m", 2,000,000,000 -> "2b", 1,000,000,000,000 -> "1t") so
 * big balances are easy to read at a glance - the display-side counterpart
 * to /shop's k/m/b/t amount shorthand on input. Anything under 1,000 is
 * just shown as a plain number.
 */
public final class CoinFormat {

    private CoinFormat() {
    }

    /** "1.2k" style shorthand only. */
    public static String format(long amount) {
        long abs = Math.abs(amount);
        String sign = amount < 0 ? "-" : "";

        if (abs < 1000) return sign + abs;

        double value;
        String suffix;
        if (abs >= 1_000_000_000_000L) {
            value = abs / 1_000_000_000_000.0;
            suffix = "t";
        } else if (abs >= 1_000_000_000L) {
            value = abs / 1_000_000_000.0;
            suffix = "b";
        } else if (abs >= 1_000_000L) {
            value = abs / 1_000_000.0;
            suffix = "m";
        } else {
            value = abs / 1_000.0;
            suffix = "k";
        }

        String formatted = String.format(Locale.US, "%.1f", value);
        if (formatted.endsWith(".0")) {
            formatted = formatted.substring(0, formatted.length() - 2);
        }
        return sign + formatted + suffix;
    }

    /** Plain comma-grouped number, for showing the exact amount alongside the shorthand. */
    public static String exact(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    /** "1.2k (1,234)" combined display - shorthand first since that's what reads fastest, exact count in parens. */
    public static String formatWithExact(long amount) {
        String shorthand = format(amount);
        String exact = exact(amount);
        if (shorthand.equals(exact)) return exact;
        return shorthand + " (" + exact + ")";
    }
}
