package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
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
    private final CurrencyManager currencyManager;
    private final EconomyManager economyManager;
    private final MessageManager messageManager;

    public CurrencyCommandHandler(JavaPlugin plugin, CurrencyManager currencyManager, EconomyManager economyManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.currencyManager = currencyManager;
        this.economyManager = economyManager;
        this.messageManager = messageManager;
    }

    public boolean handle(CommandSender sender, Currency currency, String label, String[] args) {
        if (label.equalsIgnoreCase("eco") || label.equalsIgnoreCase("economy")) {
            return handleEco(sender, label, args);
        }
        if (args.length == 0) {
            handleBalance(sender, currency, label, args);
            return true;
        }
        messageManager.send(sender, "usage-currency-balance", Map.of("currency", label));
        return true;
    }

    private boolean handleEco(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            messageManager.send(sender, "usage-eco", Map.of("currency", label));
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give" -> handleGive(sender, label, args);
            case "take" -> handleTake(sender, label, args);
            case "reset" -> handleReset(sender, label, args);
            case "set" -> handleSet(sender, label, args);
            default -> messageManager.send(sender, "usage-eco", Map.of("currency", label));
        }
        return true;
    }

    private void handleGive(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.eco.give")) {
            messageManager.send(sender, "no-permission", null);
            return;
        }
        ParsedEcoCommand parsed = parseEcoCommand(sender, label, args, true);
        if (parsed == null) {
            return;
        }
        if (parsed.amount().compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(sender, "invalid-amount", null);
            return;
        }
        BigDecimal actual = economyManager.addBalance(parsed.target().getUniqueId(), parsed.currency(), parsed.amount());
        if (actual.compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(sender, maxBalanceMessage(parsed.currency()), null);
            return;
        }
        Map<String, String> placeholders = basePlaceholders(parsed.currency(), parsed.target().getName(), actual);
        messageManager.send(sender, "eco.give", placeholders);
        if (parsed.target().isOnline()) {
            messageManager.send(parsed.target().getPlayer(), "eco.received", placeholders);
        }
        logAction(sender, "give", actual, parsed.currency(), parsed.target().getName());
    }

    private void handleTake(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.eco.take")) {
            messageManager.send(sender, "no-permission", null);
            return;
        }
        ParsedEcoCommand parsed = parseEcoCommand(sender, label, args, true);
        if (parsed == null) {
            return;
        }
        if (parsed.amount().compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(sender, "invalid-amount", null);
            return;
        }
        BigDecimal actual = economyManager.subtractBalance(parsed.target().getUniqueId(), parsed.currency(), parsed.amount());
        if (actual.compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(sender, minBalanceMessage(parsed.currency()), null);
            return;
        }
        Map<String, String> placeholders = basePlaceholders(parsed.currency(), parsed.target().getName(), actual);
        messageManager.send(sender, "eco.take", placeholders);
        logAction(sender, "take", actual, parsed.currency(), parsed.target().getName());
    }

    private void handleSet(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.eco.set")) {
            messageManager.send(sender, "no-permission", null);
            return;
        }
        ParsedEcoCommand parsed = parseEcoCommand(sender, label, args, true);
        if (parsed == null) {
            return;
        }
        economyManager.setBalance(parsed.target().getUniqueId(), parsed.currency(), parsed.amount());
        messageManager.send(sender, "eco.set", basePlaceholders(parsed.currency(), parsed.target().getName(), parsed.amount()));
        logAction(sender, "set", parsed.amount(), parsed.currency(), parsed.target().getName());
    }

    private void handleReset(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.eco.reset")) {
            messageManager.send(sender, "no-permission", null);
            return;
        }
        ParsedEcoCommand parsed = parseEcoCommand(sender, label, args, false);
        if (parsed == null) {
            return;
        }
        economyManager.resetBalance(parsed.target().getUniqueId(), parsed.currency());
        messageManager.send(sender, "eco.reset", basePlaceholders(parsed.currency(), parsed.target().getName(), BigDecimal.ZERO));
        logAction(sender, "reset", BigDecimal.ZERO, parsed.currency(), parsed.target().getName());
    }

    private ParsedEcoCommand parseEcoCommand(CommandSender sender, String label, String[] args, boolean requiresAmount) {
        int requiredLength = requiresAmount ? 4 : 3;
        if (args.length < requiredLength) {
            messageManager.send(sender, "usage-eco", Map.of("currency", label));
            return null;
        }
        Optional<OfflinePlayer> targetOpt = getOfflinePlayer(args[1]);
        if (targetOpt.isEmpty()) {
            messageManager.send(sender, "player-not-found", null);
            return null;
        }
        Currency selectedCurrency = getCurrency(args[2]);
        if (selectedCurrency == null) {
            messageManager.send(sender, "currency.not-found", null);
            return null;
        }
        BigDecimal amount = BigDecimal.ZERO;
        if (requiresAmount) {
            amount = parseAmount(args[3]);
            if (amount == null) {
                messageManager.send(sender, "invalid-amount", null);
                return null;
            }
        }
        return new ParsedEcoCommand(targetOpt.get(), selectedCurrency, amount);
    }

    private Currency getCurrency(String input) {
        Currency currency = currencyManager.getCurrency(input);
        if (currency != null) {
            return currency;
        }
        if (input.equalsIgnoreCase("shard")) {
            return currencyManager.getCurrency("shards");
        }
        return null;
    }

    private void handleBalance(CommandSender sender, Currency currency, String label, String[] args) {
        if (args.length > 0) {
            messageManager.send(sender, "usage-currency-balance", Map.of("currency", label));
            return;
        }
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "usage-currency-balance", Map.of("currency", label));
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
        messageManager.send(sender, balanceMessagePath(currency, self), placeholders);
    }

    private Map<String, String> basePlaceholders(Currency currency, String player, BigDecimal amount) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player == null ? "Unknown" : player);
        placeholders.put("amount", NumberUtil.format(amount));
        placeholders.put("symbol", currency.getSymbol());
        placeholders.put("coloredsymbol", coloredSymbol(currency));
        placeholders.put("currency", coloredCurrencyName(currency));
        return placeholders;
    }

    private String balanceMessagePath(Currency currency, boolean self) {
        String currencyKey = currency.getName().equalsIgnoreCase("shards") ? "shards" : "money";
        return "balance." + currencyKey + (self ? ".self" : ".other");
    }

    private String maxBalanceMessage(Currency currency) {
        return currency.getName().equalsIgnoreCase("shards") ? "max-shards-reached" : "max-money-reached";
    }

    private String minBalanceMessage(Currency currency) {
        return currency.getName().equalsIgnoreCase("shards") ? "min-shards-reached" : "min-money-reached";
    }

    private String coloredCurrencyName(Currency currency) {
        return currency.getName().equalsIgnoreCase("shards") ? "&5Shards" : "&aMoney";
    }

    private String coloredSymbol(Currency currency) {
        return currency.getName().equalsIgnoreCase("shards") ? "&5" + currency.getSymbol() : "&a" + currency.getSymbol();
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
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
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
            case "give" -> actor + " gave " + formatted + " " + currency.getName() + " to " + target;
            case "take" -> actor + " took " + formatted + " " + currency.getName() + " from " + target;
            case "set" -> actor + " set " + target + "'s " + currency.getName() + " balance to " + formatted;
            case "reset" -> actor + " reset " + target + "'s " + currency.getName() + " balance";
            default -> actor + " " + action + " " + formatted + " " + currency.getName() + " to " + target;
        };
        plugin.getLogger().info("[BestEconomy] " + message);
    }

    private record ParsedEcoCommand(OfflinePlayer target, Currency currency, BigDecimal amount) {
    }
}
