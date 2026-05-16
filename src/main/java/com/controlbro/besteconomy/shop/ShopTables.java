package com.controlbro.besteconomy.shop;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopTables {
    private static final String CREATE_ACCOUNTS = """
        CREATE TABLE IF NOT EXISTS shop_accounts (
            id INT AUTO_INCREMENT PRIMARY KEY,
            uuid VARCHAR(36) NOT NULL UNIQUE,
            username VARCHAR(16) NOT NULL,
            password_hash VARCHAR(255) NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        )
        """;

    private static final String CREATE_PENDING_COMMANDS = """
        CREATE TABLE IF NOT EXISTS shop_pending_commands (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            order_id BIGINT NULL,
            uuid VARCHAR(36) NOT NULL,
            username VARCHAR(16) NOT NULL,
            command TEXT NOT NULL,
            signature VARCHAR(128) NOT NULL,
            status ENUM('pending','processing','completed','failed') NOT NULL DEFAULT 'pending',
            attempts INT NOT NULL DEFAULT 0,
            last_error TEXT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            processed_at TIMESTAMP NULL
        )
        """;

    private static final String CREATE_API_LOGS = """
        CREATE TABLE IF NOT EXISTS shop_api_logs (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            action VARCHAR(64) NOT NULL,
            uuid VARCHAR(36) NULL,
            username VARCHAR(16) NULL,
            ip_address VARCHAR(64) NULL,
            success BOOLEAN NOT NULL,
            message TEXT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """;

    private final JavaPlugin plugin;
    private final ShopDatabaseManager databaseManager;

    public ShopTables(JavaPlugin plugin, ShopDatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void createAsync(Runnable onSuccess) {
        if (!databaseManager.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = databaseManager.getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_ACCOUNTS);
                statement.executeUpdate(CREATE_PENDING_COMMANDS);
                statement.executeUpdate(CREATE_API_LOGS);
                plugin.getLogger().info("Webshop MySQL tables are ready.");
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (SQLException ex) {
                plugin.getLogger().severe("Failed to create webshop MySQL tables: " + ex.getMessage());
            }
        });
    }
}
