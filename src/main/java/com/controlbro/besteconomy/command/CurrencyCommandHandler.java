package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.NumberUtil;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CurrencyCommandHandler {
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messageManager;

    public CurrencyCommandHandler(JavaPlugin plugin, EconomyManager economyManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messageManager = messageManager;
    }

    public boolean handle(CommandSender sender, Currency currency, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messageManager.send(sender, "usage-eco", Map.of("currency", label));
                return true;
            }
            if (!sender.hasPermission("besteconomy.balance")) {
                messageManager.send(sender, "no-permission", null);
                return true;
            }
            sendBalance(player, player.getUniqueId(), player.getName(), currency, true);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give" -> handleGive(sender, currency, label, args);
            case "take" -> handleTake(sender, currency, label, args);
            case "reset" -> handleReset(sender, currency, label, args);
            case "pay" -> handlePay(sender, currency, label, args);
            case "balance", "bal" -> handleBalance(sender, currency, label, args);
            default -> messageManager.send(sender, "usage-eco", Map.of("currency", label));
        }
        return true;
    }

    private void handleGive(CommandSender sender, Currency currency, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.eco.give")) {
            messageManager.send(sender, "no-permission", null);
            return;
        }
        if (args.length < 3) {
            messageManager.send(sender, "usage-eco", Map.of("currency", label));
            return;
        }
        Optional<OfflinePlayer> targetOpt = getOfflinePlayer(args[1]);
        if (targetOpt.isEmpty()) {
            messageManager.send(sender, "player-not-found", null);
            return;
        }
        BigDecimal amount = parseAmount(args[2]);
        if (amount == null) {
            messageManager.send(sender, "invalid-amount", null);
            return;
        }
        OfflinePlayer target = targetOpt.get();
        BigDecimal actual = economyManager.addBalance(target.getUniqueId(), currency, amount);
        if (actual.compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(sender, "max-money-reached", null);
            return;
        }
        Map<String, String> placeholders = basePlaceholders(currency, target.getName(), actual);
        messageManager.send(sender, "eco.give", placeholders);
        if (target.isOnline()) {
            messageManager.send(target.getPlayer(), "eco.received", placeholders);
        }
        logAction(sender, "give", actual, currency, target.getName());
    }

    private void handleTake(CommandSender sender, Currency currency, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.eco.take")) {
            messageManager.send(sender, "no-permission", null);
            return;
        }
        if (args.length < 3) {
            messageManager.send(sender, "usage-eco", Map.of("currency", label));
            return;
        }
        Optional<OfflinePlayer> targetOpt = getOfflinePlayer(args[1]);
        if (targetOpt.isEmpty()) {
            messageManager.send(sender, "player-not-found", null);
            return;
        }
        BigDecimal amount = parseAmount(args[2]);
        if (amount == null) {
            messageManager.send(sender, "invalid-amount", null);
            return;
        }
        OfflinePlayer target = targetOpt.get();
        BigDecimal actual = economyManager.subtractBalance(target.getUniqueId(), currency, amount);
        if (actual.compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(sender, "min-money-reached", null);
            return;
        }
        Map<String, String> placeholders = basePlaceholders(currency, target.getName(), actual);
        messageManager.send(sender, "eco.take", placeholders);
        logAction(sender, "take", actual, currency, target.getName());
    }

    private void handleReset(CommandSender sender, Currency currency, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.eco.reset")) {
            messageManager.send(sender, "no-permission", null);
            return;
        }
        if (args.length < 2) {
            messageManager.send(sender, "usage-eco", Map.of("currency", label));
            return;
        }
        Optional<OfflinePlayer> targetOpt = getOfflinePlayer(args[1]);
        if (targetOpt.isEmpty()) {
            messageManager.send(sender, "player-not-found", null);
            return;
        }
        OfflinePlayer target = targetOpt.get();
        economyManager.resetBalance(target.getUniqueId(), currency);
        Map<String, String> placeholders = basePlaceholders(currency, target.getName(), BigDecimal.ZERO);
        messageManager.send(sender, "eco.reset", placeholders);
        logAction(sender, "reset", BigDecimal.ZERO, currency, target.getName());
    }

    private void handlePay(CommandSender sender, Currency currency, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.pay")) {
            messageManager.send(sender, "no-permission", null);
            return;
        }
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "usage-eco", Map.of("currency", label));
            return;
        }
        if (args.length < 3) {
            messageManager.send(sender, "usage-eco", Map.of("currency", label));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messageManager.send(sender, "player-not-found", null);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messageManager.send(sender, "cannot-pay-self", null);
            return;
        }
        BigDecimal amount = parseAmount(args[2]);
        if (amount == null) {
            messageManager.send(sender, "invalid-amount", null);
            return;
        }
        BigDecimal available = economyManager.getAvailableToSpend(player.getUniqueId(), currency);
        if (available.compareTo(amount) < 0) {
            messageManager.send(sender, "insufficient-funds", null);
            return;
        }
        BigDecimal possible = amount;
        BigDecimal targetBalance = economyManager.getBalance(target.getUniqueId(), currency);
        BigDecimal maxAdd = currency.getMaxMoney().subtract(targetBalance);
        if (maxAdd.compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(sender, "max-money-reached", null);
            return;
        }
        if (maxAdd.compareTo(possible) < 0) {
            possible = maxAdd;
        }
        economyManager.subtractBalance(player.getUniqueId(), currency, possible);
        economyManager.addBalance(target.getUniqueId(), currency, possible);
        Map<String, String> placeholders = basePlaceholders(currency, target.getName(), possible);
        messageManager.send(sender, "pay.sent", placeholders);
        messageManager.send(target, "pay.received", Map.of(
            "player", player.getName(),
            "amount", NumberUtil.format(possible),
            "symbol", currency.getSymbol(),
            "currency", currency.getName()
        ));
        logAction(sender, "pay", possible, currency, target.getName());
    }

    private void handleBalance(CommandSender sender, Currency currency, String label, String[] args) {
        if (args.length > 1) {
            if (!sender.hasPermission("besteconomy.balance.others")) {
                messageManager.send(sender, "no-permission", null);
                return;
            }
            Optional<OfflinePlayer> targetOpt = getOfflinePlayer(args[1]);
            if (targetOpt.isEmpty()) {
                messageManager.send(sender, "player-not-found", null);
                return;
            }
            OfflinePlayer target = targetOpt.get();
            sendBalance(sender, target.getUniqueId(), target.getName(), currency, false);
            return;
        }
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "usage-eco", Map.of("currency", label));
            return;
        }
        if (!sender.hasPermission("besteconomy.balance")) {
            messageManager.send(sender, "no-permission", null);
            return;
        }
        sendBalance(sender, player.getUniqueId(), player.getName(), currency, true);
    }

    private void sendBalance(CommandSender sender, UUID uuid, String name, Currency currency, boolean self) {
        BigDecimal balance = economyManager.getBalance(uuid, currency);
        Map<String, String> placeholders = basePlaceholders(currency, name, balance);
        messageManager.send(sender, self ? "balance.self" : "balance.other", placeholders);
    }

    private Map<String, String> basePlaceholders(Currency currency, String player, BigDecimal amount) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player == null ? "Unknown" : player);
        placeholders.put("amount", NumberUtil.format(amount));
        placeholders.put("symbol", currency.getSymbol());
        placeholders.put("currency", currency.getName());
        return placeholders;
    }

    private Optional<OfflinePlayer> getOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online);
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return Optional.of(cached);
        }
        return Optional.empty();
    }

    private BigDecimal parseAmount(String input) {
        try {
            BigDecimal amount = new BigDecimal(input);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return amount;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void logAction(CommandSender sender, String action, BigDecimal amount, Currency currency, String target) {
        if (!plugin.getConfig().getBoolean("economy-log-enabled", false)) {
            return;
        }
        String actor = sender.getName();
        String formatted = currency.getSymbol() + NumberUtil.format(amount);
        String message = switch (action) {
            case "give" -> actor + " gave " + formatted + " to " + target;
            case "take" -> actor + " took " + formatted + " from " + target;
            case "reset" -> actor + " reset " + target + "'s balance";
            case "pay" -> actor + " paid " + formatted + " to " + target;
            default -> actor + " " + action + " " + formatted + " to " + target;
        };
        plugin.getLogger().info("[BestEconomy] " + message);
    }
}
