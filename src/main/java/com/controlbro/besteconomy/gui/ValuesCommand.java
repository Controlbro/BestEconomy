package com.controlbro.besteconomy.gui;

import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ValuesCommand implements CommandExecutor {
    private final ShopGuiService shopGuiService;
    private final MessageManager messageManager;

    public ValuesCommand(ShopGuiService shopGuiService, MessageManager messageManager) {
        this.shopGuiService = shopGuiService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "shop.players-only", null);
            return true;
        }
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                messageManager.send(sender, "invalid-amount", null);
                return true;
            }
        }
        shopGuiService.openValues(player, page);
        return true;
    }
}
