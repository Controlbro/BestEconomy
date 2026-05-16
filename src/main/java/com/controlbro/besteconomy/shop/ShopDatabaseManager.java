package com.controlbro.besteconomy.shop;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopDatabaseManager {
    private static final String UNCHANGED_USERNAME = "CHANGE_THIS_USERNAME";
    private static final String UNCHANGED_PASSWORD = "CHANGE_THIS_PASSWORD";
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
        properties.setProperty("user", config.getString("mysql.username", ""));
        properties.setProperty("password", config.getString("mysql.password", ""));
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
        String apiKey = config.getString("webshop.api-key", "");
        String username = config.getString("mysql.username", "");
        String password = config.getString("mysql.password", "");
        boolean configured = apiKey != null && apiKey.length() >= 32
            && !apiKey.equals("CHANGE_THIS_TO_A_LONG_RANDOM_SECRET")
            && username != null && !username.isBlank() && !username.equals(UNCHANGED_USERNAME)
            && password != null && !password.isBlank() && !password.equals(UNCHANGED_PASSWORD);
        if (!configured && config.getBoolean("webshop.enabled", true)) {
            plugin.getLogger().warning("Webshop is enabled but MySQL credentials or api-key are not configured. Raw secrets were not logged.");
        }
        return configured;
    }
}
