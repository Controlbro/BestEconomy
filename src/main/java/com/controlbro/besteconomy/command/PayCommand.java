package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.NumberUtil;
import java.math.BigDecimal;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PayCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messageManager;
    private final Currency currency;

    public PayCommand(JavaPlugin plugin, EconomyManager economyManager, MessageManager messageManager, Currency currency) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messageManager = messageManager;
        this.currency = currency;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.pay")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "usage-pay", null);
            return true;
        }
        if (args.length < 2) {
            messageManager.send(sender, "usage-pay", null);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messageManager.send(sender, "player-not-found", null);
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messageManager.send(sender, "cannot-pay-self", null);
            return true;
        }
        BigDecimal amount = parseAmount(args[1]);
        if (amount == null) {
            messageManager.send(sender, "invalid-amount", null);
            return true;
        }
        BigDecimal available = economyManager.getAvailableToSpend(player.getUniqueId(), currency);
        if (available.compareTo(amount) < 0) {
            messageManager.send(sender, "insufficient-funds", null);
            return true;
        }
        BigDecimal targetBalance = economyManager.getBalance(target.getUniqueId(), currency);
        BigDecimal maxAdd = currency.getMaxMoney().subtract(targetBalance);
        if (maxAdd.compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(sender, "max-money-reached", null);
            return true;
        }
        BigDecimal possible = amount.min(maxAdd);
        economyManager.subtractBalance(player.getUniqueId(), currency, possible);
        economyManager.addBalance(target.getUniqueId(), currency, possible);
        messageManager.send(sender, "pay.sent", Map.of(
            "player", target.getName(),
            "amount", NumberUtil.format(possible),
            "symbol", currency.getSymbol(),
            "coloredsymbol", coloredSymbol(),
            "currency", coloredCurrencyName()
        ));
        messageManager.send(target, "pay.received", Map.of(
            "player", player.getName(),
            "amount", NumberUtil.format(possible),
            "symbol", currency.getSymbol(),
            "coloredsymbol", coloredSymbol(),
            "currency", coloredCurrencyName()
        ));
        logPay(sender.getName(), possible, target.getName());
        return true;
    }

    private void logPay(String actor, BigDecimal amount, String target) {
        if (!plugin.getConfig().getBoolean("economy-log-enabled", false)) {
            return;
        }
        String formatted = currency.getSymbol() + NumberUtil.format(amount);
        plugin.getLogger().info("[BestEconomy] " + actor + " paid " + formatted + " to " + target);
    }

    private String coloredCurrencyName() {
        return currency.getName().equalsIgnoreCase("shards") ? "&5Shards" : "&aMoney";
    }

    private String coloredSymbol() {
        return currency.getName().equalsIgnoreCase("shards") ? "&5" + currency.getSymbol() : "&a" + currency.getSymbol();
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

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return java.util.Collections.emptyList();
    }
}
