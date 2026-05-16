package com.controlbro.besteconomy.shop;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopDatabaseManager {
    private static final String UNCHANGED_USERNAME = "CHANGE_THIS_USERNAME";
    private static final String UNCHANGED_PASSWORD = "CHANGE_THIS_PASSWORD";
    private static final String UNCHANGED_API_KEY = "CHANGE_THIS_TO_A_LONG_RANDOM_SECRET";
    private static final int RECOMMENDED_MIN_API_KEY_LENGTH = 32;
    private final JavaPlugin plugin;
    private final boolean enabled;
    private final String jdbcUrl;
    private final Properties properties;

    public ShopDatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("webshop.enabled", true) && isConfigured(config);
        this.jdbcUrl = "jdbc:mysql://" + config.getString("mysql.host", "localhost") + ":"
            + config.getInt("mysql.port", 3306) + "/" + config.getString("mysql.database", "besteconomy")
            + "?useSSL=" + config.getBoolean("mysql.use-ssl", false)
            + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        this.properties = new Properties();
        properties.setProperty("user", safeString(config.getString("mysql.username", "")));
        properties.setProperty("password", safeString(config.getString("mysql.password", "")));
        properties.setProperty("connectTimeout", String.valueOf(config.getInt("mysql.connection-timeout-ms", 10000)));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Connection getConnection() throws SQLException {
        if (!enabled) {
            throw new SQLException("Webshop MySQL is not configured");
        }
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    private boolean isConfigured(FileConfiguration config) {
        String apiKey = safeString(config.getString("webshop.api-key", ""));
        String username = safeString(config.getString("mysql.username", ""));
        String password = safeString(config.getString("mysql.password", ""));
        List<String> missingFields = new ArrayList<>();
        if (username.isBlank() || username.equals(UNCHANGED_USERNAME)) {
            missingFields.add("mysql.username");
        }
        // Blank MySQL passwords are valid for some local setups, but the unchanged placeholder is not.
        if (password.equals(UNCHANGED_PASSWORD)) {
            missingFields.add("mysql.password");
        }
        if (apiKey.isBlank() || apiKey.equals(UNCHANGED_API_KEY)) {
            missingFields.add("webshop.api-key");
        }
        if (!missingFields.isEmpty()) {
            if (config.getBoolean("webshop.enabled", true)) {
                plugin.getLogger().warning("Webshop is enabled but required config values are missing or still placeholders: "
                    + String.join(", ", missingFields) + ". Raw secrets were not logged.");
            }
            return false;
        }
        if (apiKey.length() < RECOMMENDED_MIN_API_KEY_LENGTH) {
            plugin.getLogger().warning("webshop.api-key is configured but shorter than the recommended "
                + RECOMMENDED_MIN_API_KEY_LENGTH + " characters. Raw secrets were not logged.");
        }
        return true;
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
