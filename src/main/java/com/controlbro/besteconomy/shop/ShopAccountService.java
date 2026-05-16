package com.controlbro.besteconomy.shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.java.JavaPlugin;
import org.mindrot.jbcrypt.BCrypt;

public class ShopAccountService {
    private final JavaPlugin plugin;
    private final ShopDatabaseManager databaseManager;

    public ShopAccountService(JavaPlugin plugin, ShopDatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public boolean isEnabled() {
        return databaseManager.isEnabled();
    }

    public CompletableFuture<Boolean> accountExists(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) {
                return false;
            }
            String sql = "SELECT 1 FROM shop_accounts WHERE uuid = ? LIMIT 1";
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("Failed checking webshop account: " + ex.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> createAccount(UUID uuid, String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) {
                return false;
            }
            String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
            String sql = "INSERT INTO shop_accounts (uuid, username, password_hash) VALUES (?, ?, ?)";
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, username);
                statement.setString(3, hash);
                statement.executeUpdate();
                logApi(connection, "account_create", uuid.toString(), username, true, "Account created by player in-game");
                plugin.getLogger().info("Webshop account created for " + username + " (" + uuid + ").");
                return true;
            } catch (SQLException ex) {
                plugin.getLogger().warning("Failed creating webshop account for " + username + ": " + ex.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> resetPassword(UUID uuid, String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) {
                return false;
            }
            String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
            String sql = "UPDATE shop_accounts SET username = ?, password_hash = ? WHERE uuid = ?";
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);
                statement.setString(2, hash);
                statement.setString(3, uuid.toString());
                boolean updated = statement.executeUpdate() > 0;
                logApi(connection, "password_reset", uuid.toString(), username, updated,
                    updated ? "Password reset by player in-game" : "Password reset requested before account existed");
                if (updated) {
                    plugin.getLogger().info("Webshop password reset for " + username + " (" + uuid + ").");
                }
                return updated;
            } catch (SQLException ex) {
                plugin.getLogger().warning("Failed resetting webshop password for " + username + ": " + ex.getMessage());
                return false;
            }
        });
    }

    private void logApi(Connection connection, String action, String uuid, String username, boolean success, String message) throws SQLException {
        String sql = "INSERT INTO shop_api_logs (action, uuid, username, success, message) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, action);
            statement.setString(2, uuid);
            statement.setString(3, username);
            statement.setBoolean(4, success);
            statement.setString(5, message);
            statement.executeUpdate();
        }
    }
}
