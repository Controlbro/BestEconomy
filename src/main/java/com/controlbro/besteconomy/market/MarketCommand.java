package com.controlbro.besteconomy.market;

import com.controlbro.besteconomy.message.MessageManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class MarketCommand implements CommandExecutor, TabCompleter {
    private final MarketService marketService;
    private final MessageManager messageManager;

    public MarketCommand(MarketService marketService, MessageManager messageManager) {
        this.marketService = marketService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "shop.players-only", null);
            return true;
        }
        if (!player.hasPermission("besteconomy.market.use")) {
            messageManager.send(player, "no-permission", null);
            return true;
        }
        if (args.length == 0) {
            marketService.openMarket(player, 0, null);
            return true;
        }
        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 2) {
                messageManager.send(player, "market.create-usage", null);
                return true;
            }
            marketService.createStall(player, join(args, 1));
            return true;
        }
        if (args[0].equalsIgnoreCase("name") || args[0].equalsIgnoreCase("rename")) {
            if (args.length < 2) {
                messageManager.send(player, "market.name-usage", null);
                return true;
            }
            marketService.renameStall(player, join(args, 1));
            return true;
        }
        if (args[0].equalsIgnoreCase("manage")) {
            marketService.openManage(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("search")) {
            if (args.length < 2) {
                messageManager.send(player, "market.search-usage", null);
                return true;
            }
            marketService.openMarket(player, 0, join(args, 1));
            return true;
        }
        messageManager.send(player, "market.usage", null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String option : List.of("create", "name", "manage", "search")) {
                if (option.startsWith(args[0].toLowerCase())) {
                    completions.add(option);
                }
            }
            return completions;
        }
        return List.of();
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
