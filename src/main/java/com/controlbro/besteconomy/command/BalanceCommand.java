package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.NumberUtil;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
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
        if (args.length > 0) {
            messageManager.send(sender, "usage-balance", null);
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
        messageManager.send(sender, balanceMessagePath(self), placeholders);
    }

    private String coloredCurrencyName() {
        return currency.getName().equalsIgnoreCase("shards") ? "&5Shards" : "&aMoney";
    }

    private String coloredSymbol() {
        return currency.getName().equalsIgnoreCase("shards") ? "&5" + currency.getSymbol() : "&a" + currency.getSymbol();
    }

    private String balanceMessagePath(boolean self) {
        String currencyKey = currency.getName().equalsIgnoreCase("shards") ? "shards" : "money";
        return "balance." + currencyKey + (self ? ".self" : ".other");
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return CurrencyTabCompleter.completeBalance(sender, args);
    }
}
