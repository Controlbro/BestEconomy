package com.controlbro.besteconomy.shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ShopPendingCommandService {
    private final JavaPlugin plugin;
    private final ShopDatabaseManager databaseManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private BukkitTask task;

    public ShopPendingCommandService(JavaPlugin plugin, ShopDatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public boolean isEnabled() {
        return databaseManager.isEnabled();
    }

    public void start() {
        stop();
        if (!isEnabled()) {
            return;
        }
        long intervalSeconds = plugin.getConfig().getLong("webshop.pending-check-seconds", 60);
        if (intervalSeconds <= 0) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processPending, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void processPendingNow() {
        if (!isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::processPending);
    }

    public void processPending() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        int completed = 0;
        int failed = 0;
        try {
            List<PendingCommand> commands = fetchPendingCommands();
            for (PendingCommand pendingCommand : commands) {
                if (!claim(pendingCommand.id())) {
                    continue;
                }
                ProcessResult result = processClaimed(pendingCommand);
                if (result.success()) {
                    completed++;
                } else {
                    failed++;
                }
            }
            if (completed > 0 || failed > 0) {
                plugin.getLogger().info("Webshop pending commands processed. completed=" + completed + ", failed=" + failed);
            }
        } finally {
            running.set(false);
        }
    }

    private List<PendingCommand> fetchPendingCommands() {
        int limit = Math.max(1, plugin.getConfig().getInt("webshop.max-commands-per-check", 50));
        String sql = "SELECT id, order_id, uuid, username, command, signature FROM shop_pending_commands "
            + "WHERE status = 'pending' ORDER BY id ASC LIMIT ?";
        List<PendingCommand> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Long orderId = resultSet.getObject("order_id", Long.class);
                    results.add(new PendingCommand(
                        resultSet.getLong("id"),
                        orderId,
                        resultSet.getString("uuid"),
                        resultSet.getString("username"),
                        resultSet.getString("command"),
                        resultSet.getString("signature")
                    ));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed fetching webshop pending commands: " + ex.getMessage());
        }
        return results;
    }

    private boolean claim(long id) {
        // Atomic status transition prevents duplicate execution across repeated tasks or multiple server instances.
        String sql = "UPDATE shop_pending_commands SET status = 'processing', attempts = attempts + 1 WHERE id = ? AND status = 'pending'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed claiming webshop pending command " + id + ": " + ex.getMessage());
            return false;
        }
    }

    private ProcessResult processClaimed(PendingCommand pendingCommand) {
        String apiKey = plugin.getConfig().getString("webshop.api-key", "");
        String orderId = pendingCommand.orderId() == null ? "" : String.valueOf(pendingCommand.orderId());
        if (!ShopSignatureUtil.isValid(pendingCommand.signature(), orderId, pendingCommand.uuid(), pendingCommand.username(), pendingCommand.command(), apiKey)) {
            String error = "Invalid command signature";
            plugin.getLogger().warning("Rejected webshop command " + pendingCommand.id() + " for " + pendingCommand.username() + ": " + error);
            markFailed(pendingCommand.id(), error);
            return new ProcessResult(false);
        }

        String command = replacePlaceholders(pendingCommand).replaceFirst("^/", "");
        try {
            // Bukkit command dispatch must happen on the main server thread, so the async DB worker waits for this sync result.
            Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(plugin,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            boolean dispatched = future.get();
            if (dispatched) {
                markCompleted(pendingCommand.id());
                return new ProcessResult(true);
            }
            String error = "Bukkit rejected command dispatch";
            plugin.getLogger().warning("Failed webshop command " + pendingCommand.id() + ": " + error);
            markFailed(pendingCommand.id(), error);
            return new ProcessResult(false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            markFailed(pendingCommand.id(), "Interrupted while dispatching command");
            return new ProcessResult(false);
        } catch (ExecutionException ex) {
            String error = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            plugin.getLogger().warning("Failed webshop command " + pendingCommand.id() + ": " + error);
            markFailed(pendingCommand.id(), error);
            return new ProcessResult(false);
        }
    }

    private String replacePlaceholders(PendingCommand pendingCommand) {
        return pendingCommand.command()
            .replace("{player}", pendingCommand.username())
            .replace("{username}", pendingCommand.username())
            .replace("{uuid}", pendingCommand.uuid());
    }

    private void markCompleted(long id) {
        String sql = "UPDATE shop_pending_commands SET status = 'completed', processed_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed marking webshop command " + id + " completed: " + ex.getMessage());
        }
    }

    private void markFailed(long id, String error) {
        String sql = "UPDATE shop_pending_commands SET status = 'failed', last_error = ?, processed_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, error);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed marking webshop command " + id + " failed: " + ex.getMessage());
        }
    }

    private record PendingCommand(long id, Long orderId, String uuid, String username, String command, String signature) {
    }

    private record ProcessResult(boolean success) {
    }
}
