package com.controlbro.besteconomy.currency;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public class CurrencyManager {
    private final JavaPlugin plugin;
    private final Map<String, Currency> currencies = new HashMap<>();

    public CurrencyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        currencies.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("currencies");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection currencySection = section.getConfigurationSection(key);
            if (currencySection == null) {
                continue;
            }
            String symbol = currencySection.getString("symbol", "$");
            String alias = currencySection.getString("command-alias", key);
            BigDecimal starting = new BigDecimal(currencySection.getString("starting-balance", "0"));
            BigDecimal max = new BigDecimal(currencySection.getString("max-money", "0"));
            BigDecimal min = new BigDecimal(currencySection.getString("min-money", "0"));
            currencies.put(key.toLowerCase(), new Currency(key, symbol, alias, starting, max, min));
        }
    }

    public Currency getCurrency(String name) {
        return currencies.get(name.toLowerCase());
    }

    public Currency getDefaultCurrency() {
        String configuredDefault = plugin.getConfig().getString("default-currency", "money");
        Currency defaultCurrency = getCurrency(configuredDefault);
        if (defaultCurrency != null) {
            return defaultCurrency;
        }
        return currencies.get("default");
    }

    public Collection<Currency> getCurrencies() {
        return Collections.unmodifiableCollection(currencies.values());
    }
}
