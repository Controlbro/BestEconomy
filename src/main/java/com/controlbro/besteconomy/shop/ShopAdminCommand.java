package com.controlbro.besteconomy.shop;

import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class ShopAdminCommand implements CommandExecutor, TabCompleter {
    private final ShopPendingCommandService pendingCommandService;
    private final MessageManager messageManager;

    public ShopAdminCommand(ShopPendingCommandService pendingCommandService, MessageManager messageManager) {
        this.pendingCommandService = pendingCommandService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shop.admin")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("processpending")) {
            if (!pendingCommandService.isEnabled()) {
                messageManager.send(sender, "shop.disabled", null);
                return true;
            }
            pendingCommandService.processPendingNow();
            messageManager.send(sender, "shop.pending-started", null);
            return true;
        }
        messageManager.send(sender, "shop.admin-usage", null);
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("shop.admin")) {
            return java.util.List.of("processpending");
        }
        return java.util.List.of();
    }
}
