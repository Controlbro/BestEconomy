package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.NumberUtil;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor, TabCompleter {
    private final EconomyManager economyManager;
    private final MessageManager messageManager;
    private final Currency currency;

    public BalanceCommand(EconomyManager economyManager, MessageManager messageManager, Currency currency) {
        this.economyManager = economyManager;
        this.messageManager = messageManager;
        this.currency = currency;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) {
            messageManager.send(sender, "usage-balance", null);
            return true;
        }
        if (args.length == 1) {
            if (!sender.hasPermission("besteconomy.balance.others")) {
                messageManager.send(sender, "no-permission", null);
                return true;
            }
            Optional<OfflinePlayer> targetOpt = getOfflinePlayer(args[0]);
            if (targetOpt.isEmpty()) {
                messageManager.send(sender, "player-not-found", null);
                return true;
            }
            OfflinePlayer target = targetOpt.get();
            sendBalance(sender, target.getName(), economyManager.getBalance(target.getUniqueId(), currency), false);
            return true;
        }
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "usage-balance", null);
            return true;
        }
        if (!sender.hasPermission("besteconomy.balance")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        sendBalance(sender, player.getName(), economyManager.getBalance(player.getUniqueId(), currency), true);
        return true;
    }

    private void sendBalance(CommandSender sender, String player, BigDecimal amount, boolean self) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player == null ? "Unknown" : player);
        placeholders.put("amount", NumberUtil.format(amount));
        placeholders.put("symbol", currency.getSymbol());
        placeholders.put("coloredsymbol", coloredSymbol());
        placeholders.put("currency", coloredCurrencyName());
        messageManager.send(sender, self ? "balance.self" : "balance.other", placeholders);
    }

    private String coloredCurrencyName() {
        return currency.getName().equalsIgnoreCase("shards") ? "&5Shards" : "&aMoney";
    }

    private String coloredSymbol() {
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

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("besteconomy.balance.others")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return java.util.Collections.emptyList();
    }
}
