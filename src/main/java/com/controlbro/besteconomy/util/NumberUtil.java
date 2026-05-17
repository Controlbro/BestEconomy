package com.controlbro.besteconomy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberUtil {
    private NumberUtil() {
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
