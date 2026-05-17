package com.controlbro.besteconomy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

public final class NumberUtil {
    private static final Map<String, BigDecimal> SUFFIX_MULTIPLIERS = Map.of(
        "k", BigDecimal.valueOf(1_000L),
        "m", BigDecimal.valueOf(1_000_000L),
        "b", BigDecimal.valueOf(1_000_000_000L),
        "t", BigDecimal.valueOf(1_000_000_000_000L),
        "q", BigDecimal.valueOf(1_000_000_000_000_000L)
    );

    private NumberUtil() {
    }

    public static BigDecimal parsePositiveAmount(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().replace(",", "").toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            return null;
        }
        BigDecimal multiplier = BigDecimal.ONE;
        String suffix = normalized.substring(normalized.length() - 1);
        if (SUFFIX_MULTIPLIERS.containsKey(suffix)) {
            multiplier = SUFFIX_MULTIPLIERS.get(suffix);
            normalized = normalized.substring(0, normalized.length() - 1).trim();
            if (normalized.isEmpty()) {
                return null;
            }
        }
        try {
            BigDecimal amount = new BigDecimal(normalized).multiply(multiplier);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return amount.stripTrailingZeros();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String format(BigDecimal value) {
        if (value.abs().compareTo(BigDecimal.valueOf(1000L)) < 0) {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            DecimalFormat format = new DecimalFormat("0.##", symbols);
            format.setRoundingMode(RoundingMode.DOWN);
            return format.format(value);
        }

        BigDecimal compact = value.divide(BigDecimal.valueOf(1000L), 1, RoundingMode.DOWN).stripTrailingZeros();
        return compact.toPlainString() + "k";
    }
}
