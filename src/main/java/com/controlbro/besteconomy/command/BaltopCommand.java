package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.NumberUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class BaltopCommand implements CommandExecutor, TabCompleter {
    private static final int PER_PAGE = 10;
    private final EconomyManager economyManager;
    private final MessageManager messageManager;
    private final Currency currency;

    public BaltopCommand(EconomyManager economyManager, MessageManager messageManager, Currency currency) {
        this.economyManager = economyManager;
        this.messageManager = messageManager;
        this.currency = currency;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.baltop")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        int page = 1;
        if (args.length > 1) {
            messageManager.send(sender, "usage-baltop", null);
            return true;
        }
        if (args.length == 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                messageManager.send(sender, "invalid-amount", null);
                return true;
            }
        }
        Map<UUID, BigDecimal> top = economyManager.getTopBalances(currency);
        if (top.isEmpty()) {
            messageManager.send(sender, "baltop.no-data", null);
            return true;
        }
        List<Map.Entry<UUID, BigDecimal>> entries = new ArrayList<>(top.entrySet());
        int maxPage = (int) Math.ceil(entries.size() / (double) PER_PAGE);
        if (page < 1) {
            page = 1;
        } else if (page > maxPage) {
            page = maxPage;
        }
        messageManager.send(sender, "baltop.header", Map.of(
            "page", String.valueOf(page),
            "maxpage", String.valueOf(maxPage)
        ));
        int start = (page - 1) * PER_PAGE;
        int end = Math.min(start + PER_PAGE, entries.size());
        for (int i = start; i < end; i++) {
            Map.Entry<UUID, BigDecimal> entry = entries.get(i);
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            Map<String, String> placeholders = Map.of(
                "position", String.valueOf(i + 1),
                "player", player.getName() == null ? "Unknown" : player.getName(),
                "amount", NumberUtil.format(entry.getValue()),
                "symbol", currency.getSymbol(),
                "currency", currency.getName()
            );
            messageManager.send(sender, "baltop.format", placeholders);
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return java.util.Collections.emptyList();
    }
}
