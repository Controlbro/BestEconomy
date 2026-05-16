package com.controlbro.besteconomy.data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MySqlShardBalanceStore {
    public static final String BALANCE_TABLE = "player_balances";
    public static final String BALANCE_UUID_COLUMN = "uuid";
    public static final String BALANCE_CURRENCY_COLUMN = "currency";
    public static final String BALANCE_AMOUNT_COLUMN = "amount";
    public static final String BALANCE_CURRENCY_VALUE = "Shards";
    private static final String UNCHANGED_USERNAME = "CHANGE_THIS_USERNAME";
    private static final String UNCHANGED_PASSWORD = "CHANGE_THIS_PASSWORD";
    private static final String IDENTIFIER_PATTERN = "[A-Za-z0-9_]+";
    private final JavaPlugin plugin;
    private final boolean enabled;
    private final String jdbcUrl;
    private final Properties properties;
    private final String tableName;
    private final String uuidColumn;
    private final String currencyColumn;
    private final String amountColumn;
    private final String currencyValue;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BestEconomy-ShardBalanceWriter");
        thread.setDaemon(true);
        return thread;
    });

    public MySqlShardBalanceStore(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.tableName = identifier(config.getString("mysql.shard-balances.table", BALANCE_TABLE), BALANCE_TABLE);
        this.uuidColumn = identifier(config.getString("mysql.shard-balances.uuid-column", BALANCE_UUID_COLUMN), BALANCE_UUID_COLUMN);
        this.currencyColumn = identifier(config.getString("mysql.shard-balances.currency-column", BALANCE_CURRENCY_COLUMN), BALANCE_CURRENCY_COLUMN);
        this.amountColumn = identifier(config.getString("mysql.shard-balances.amount-column", BALANCE_AMOUNT_COLUMN), BALANCE_AMOUNT_COLUMN);
        this.currencyValue = config.getString("mysql.shard-balances.currency-value", BALANCE_CURRENCY_VALUE);
        this.enabled = config.getBoolean("mysql.enabled", false) && isConfigured(config);
        this.jdbcUrl = "jdbc:mysql://" + config.getString("mysql.host", "localhost") + ":"
            + config.getInt("mysql.port", 3306) + "/" + config.getString("mysql.database", "besteconomy")
            + "?useSSL=" + config.getBoolean("mysql.use-ssl", false)
            + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        this.properties = new Properties();
        properties.setProperty("user", safeString(config.getString("mysql.username", "")));
        properties.setProperty("password", safeString(config.getString("mysql.password", "")));
        properties.setProperty("connectTimeout", String.valueOf(config.getInt("mysql.connection-timeout-ms", 10000)));
        if (enabled) {
            createTable();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCurrencyValue() {
        return currencyValue;
    }

    public Map<UUID, BigDecimal> loadBalances() {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        if (!enabled) {
            return balances;
        }
        String sql = "SELECT " + column(uuidColumn) + ", " + column(amountColumn)
            + " FROM " + table(tableName) + " WHERE " + column(currencyColumn) + " = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currencyValue);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    balances.put(UUID.fromString(resultSet.getString(uuidColumn)), resultSet.getBigDecimal(amountColumn));
                }
            }
        } catch (IllegalArgumentException | SQLException ex) {
            plugin.getLogger().warning("Failed loading Shards balances from MySQL: " + ex.getMessage());
        }
        return balances;
    }

    public void saveBalanceAsync(UUID uuid, BigDecimal amount) {
        if (!enabled) {
            return;
        }
        writer.execute(() -> saveBalance(uuid, amount));
    }

    public void saveBalances(Map<UUID, BigDecimal> balances) {
        if (!enabled) {
            return;
        }
        try {
            writer.submit(() -> {
                for (Map.Entry<UUID, BigDecimal> entry : balances.entrySet()) {
                    saveBalance(entry.getKey(), entry.getValue());
                }
            }).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ex) {
            plugin.getLogger().warning("Failed flushing Shards balances to MySQL: " + ex.getMessage());
        }
    }

    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + table(tableName) + " ("
            + column(uuidColumn) + " VARCHAR(36) NOT NULL, "
            + column(currencyColumn) + " VARCHAR(32) NOT NULL, "
            + column(amountColumn) + " DECIMAL(32, 8) NOT NULL DEFAULT 0, "
            + "PRIMARY KEY (" + column(uuidColumn) + ", " + column(currencyColumn) + ")"
            + ")";
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            plugin.getLogger().info("MySQL Shards balance table is ready: " + tableName + ".");
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed creating MySQL Shards balance table: " + ex.getMessage());
        }
    }

    private void saveBalance(UUID uuid, BigDecimal amount) {
        String sql = "INSERT INTO " + table(tableName) + " (" + column(uuidColumn) + ", "
            + column(currencyColumn) + ", " + column(amountColumn) + ") VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE " + column(amountColumn) + " = VALUES(" + column(amountColumn) + ")";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currencyValue);
            statement.setBigDecimal(3, amount);
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed saving Shards balance to MySQL for " + uuid + ": " + ex.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    private boolean isConfigured(FileConfiguration config) {
        String username = safeString(config.getString("mysql.username", ""));
        String password = safeString(config.getString("mysql.password", ""));
        if (username.isBlank() || username.equals(UNCHANGED_USERNAME)) {
            plugin.getLogger().warning("mysql.enabled is true but mysql.username is missing or still the placeholder.");
            return false;
        }
        if (password.equals(UNCHANGED_PASSWORD)) {
            plugin.getLogger().warning("mysql.enabled is true but mysql.password is still the placeholder. Blank passwords are allowed; the placeholder is not.");
            return false;
        }
        return true;
    }

    private String identifier(String configured, String fallback) {
        String value = safeString(configured);
        if (value.matches(IDENTIFIER_PATTERN)) {
            return value;
        }
        plugin.getLogger().warning("Invalid MySQL Shards balance identifier '" + configured + "'; using '" + fallback + "'.");
        return fallback;
    }

    private String table(String value) {
        return "`" + value + "`";
    }

    private String column(String value) {
        return "`" + value + "`";
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
