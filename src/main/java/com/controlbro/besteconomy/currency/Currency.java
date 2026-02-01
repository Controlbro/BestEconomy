package com.controlbro.besteconomy.currency;

import java.math.BigDecimal;

public class Currency {
    private final String name;
    private final String symbol;
    private final String commandAlias;
    private final BigDecimal startingBalance;
    private final BigDecimal maxMoney;
    private final BigDecimal minMoney;

    public Currency(String name, String symbol, String commandAlias, BigDecimal startingBalance,
                    BigDecimal maxMoney, BigDecimal minMoney) {
        this.name = name;
        this.symbol = symbol;
        this.commandAlias = commandAlias;
        this.startingBalance = startingBalance;
        this.maxMoney = maxMoney;
        this.minMoney = minMoney;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCommandAlias() {
        return commandAlias;
    }

    public BigDecimal getStartingBalance() {
        return startingBalance;
    }

    public BigDecimal getMaxMoney() {
        return maxMoney;
    }

    public BigDecimal getMinMoney() {
        return minMoney;
    }
}
