package com.controlbro.besteconomy.gui;

import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellCommand implements CommandExecutor {
    private final ShopGuiService shopGuiService;
    private final MessageManager messageManager;

    public SellCommand(ShopGuiService shopGuiService, MessageManager messageManager) {
        this.shopGuiService = shopGuiService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "shop.players-only", null);
            return true;
        }
        shopGuiService.openSell(player);
        return true;
    }
}
