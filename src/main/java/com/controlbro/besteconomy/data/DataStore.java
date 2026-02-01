package com.controlbro.besteconomy.data;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class DataStore {
    private final JavaPlugin plugin;
    private final File file;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public Map<UUID, Map<String, BigDecimal>> load() {
        if (!file.exists()) {
            plugin.saveResource("data.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<UUID, Map<String, BigDecimal>> data = new HashMap<>();
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return data;
        }
        for (String uuidString : players.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidString);
            ConfigurationSection balancesSection = players.getConfigurationSection(uuidString);
            if (balancesSection == null) {
                continue;
            }
            Map<String, BigDecimal> balances = new HashMap<>();
            for (String currency : balancesSection.getKeys(false)) {
                String raw = balancesSection.getString(currency, "0");
                balances.put(currency.toLowerCase(), new BigDecimal(raw));
            }
            data.put(uuid, balances);
        }
        return data;
    }

    public void save(Map<UUID, Map<String, BigDecimal>> data) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection players = config.createSection("players");
        for (Map.Entry<UUID, Map<String, BigDecimal>> entry : data.entrySet()) {
            ConfigurationSection balanceSection = players.createSection(entry.getKey().toString());
            for (Map.Entry<String, BigDecimal> currency : entry.getValue().entrySet()) {
                balanceSection.set(currency.getKey(), currency.getValue().toPlainString());
            }
        }
        try {
            config.save(file);
        } catch (IOException ignored) {
            // ignored
        }
    }
}
