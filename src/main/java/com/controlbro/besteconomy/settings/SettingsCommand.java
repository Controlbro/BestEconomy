package com.controlbro.besteconomy.settings;

import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SettingsCommand implements CommandExecutor {
    private final SettingsMenuService settingsMenuService;
    private final MessageManager messageManager;

    public SettingsCommand(SettingsMenuService settingsMenuService, MessageManager messageManager) {
        this.settingsMenuService = settingsMenuService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "shop.players-only", null);
            return true;
        }
        if (!player.hasPermission("besteconomy.settings.use")) {
            messageManager.send(player, "no-permission", null);
            return true;
        }
        settingsMenuService.open(player);
        return true;
    }
}
