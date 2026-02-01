package com.controlbro.besteconomy.data;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyManager {
    private final CurrencyManager currencyManager;
    private final DataStore dataStore;
    private final Map<UUID, Map<String, BigDecimal>> balances;

    public EconomyManager(CurrencyManager currencyManager, DataStore dataStore) {
        this.currencyManager = currencyManager;
        this.dataStore = dataStore;
        this.balances = new ConcurrentHashMap<>(dataStore.load());
    }

    public void save() {
        dataStore.save(balances);
    }

    public void ensurePlayer(UUID uuid) {
        balances.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>());
        for (Currency currency : currencyManager.getCurrencies()) {
            balances.get(uuid).putIfAbsent(currency.getName().toLowerCase(), currency.getStartingBalance());
        }
    }

    public BigDecimal getBalance(UUID uuid, Currency currency) {
        ensurePlayer(uuid);
        return balances.get(uuid).getOrDefault(currency.getName().toLowerCase(), currency.getStartingBalance());
    }

    public void setBalance(UUID uuid, Currency currency, BigDecimal amount) {
        ensurePlayer(uuid);
        BigDecimal clamped = clamp(amount, currency);
        balances.get(uuid).put(currency.getName().toLowerCase(), clamped);
    }

    public BigDecimal addBalance(UUID uuid, Currency currency, BigDecimal amount) {
        ensurePlayer(uuid);
        BigDecimal current = getBalance(uuid, currency);
        BigDecimal newBalance = current.add(amount);
        BigDecimal clamped = clamp(newBalance, currency);
        balances.get(uuid).put(currency.getName().toLowerCase(), clamped);
        return clamped.subtract(current);
    }

    public BigDecimal subtractBalance(UUID uuid, Currency currency, BigDecimal amount) {
        ensurePlayer(uuid);
        BigDecimal current = getBalance(uuid, currency);
        BigDecimal newBalance = current.subtract(amount);
        BigDecimal clamped = clamp(newBalance, currency);
        balances.get(uuid).put(currency.getName().toLowerCase(), clamped);
        return current.subtract(clamped);
    }

    public BigDecimal resetBalance(UUID uuid, Currency currency) {
        ensurePlayer(uuid);
        balances.get(uuid).put(currency.getName().toLowerCase(), currency.getStartingBalance());
        return currency.getStartingBalance();
    }

    public BigDecimal getAvailableToSpend(UUID uuid, Currency currency) {
        BigDecimal current = getBalance(uuid, currency);
        return current.subtract(currency.getMinMoney());
    }

    public Map<UUID, BigDecimal> getTopBalances(Currency currency) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        balances.entrySet().stream()
            .sorted(Map.Entry.comparingByValue(Comparator.comparing((Map<String, BigDecimal> map) ->
                map.getOrDefault(currency.getName().toLowerCase(), currency.getStartingBalance())).reversed()))
            .forEach(entry -> result.put(entry.getKey(),
                entry.getValue().getOrDefault(currency.getName().toLowerCase(), currency.getStartingBalance())));
        return Collections.unmodifiableMap(result);
    }

    private BigDecimal clamp(BigDecimal value, Currency currency) {
        BigDecimal min = currency.getMinMoney();
        BigDecimal max = currency.getMaxMoney();
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }
}
