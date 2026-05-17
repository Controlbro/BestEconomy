package com.controlbro.besteconomy.market;

import com.controlbro.besteconomy.message.MessageManager;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class GiveMarketSlotsCommand implements CommandExecutor, TabCompleter {
    private final MarketService marketService;
    private final MessageManager messageManager;

    public GiveMarketSlotsCommand(MarketService marketService, MessageManager messageManager) {
        this.marketService = marketService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.market.giveslots")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        if (args.length != 2) {
            messageManager.send(sender, "market.give-slots-usage", null);
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            messageManager.send(sender, "invalid-amount", null);
            return true;
        }
        if (amount <= 0) {
            messageManager.send(sender, "invalid-amount", null);
            return true;
        }
        marketService.giveSlots(target, amount);
        messageManager.send(sender, "market.give-slots-success", Map.of(
            "player", target.getName() == null ? args[0] : target.getName(),
            "amount", String.valueOf(amount)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).toList();
        }
        return List.of();
    }
}
