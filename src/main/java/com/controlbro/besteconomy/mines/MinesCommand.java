package com.controlbro.besteconomy.mines;

import com.controlbro.besteconomy.message.MessageManager;
import java.math.BigDecimal;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class MinesCommand implements CommandExecutor, TabCompleter {
    private final MinesService minesService;
    private final MessageManager messageManager;

    public MinesCommand(MinesService minesService, MessageManager messageManager) {
        this.minesService = minesService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "shop.players-only", null);
            return true;
        }
        if (!player.hasPermission("besteconomy.mines.use")) {
            messageManager.send(player, "no-permission", null);
            return true;
        }
        if (minesService.hasActiveGame(player)) {
            minesService.openGame(player);
            return true;
        }
        if (args.length != 1) {
            messageManager.send(player, "mines.usage", null);
            return true;
        }
        BigDecimal bet;
        try {
            bet = new BigDecimal(args[0]);
        } catch (NumberFormatException ex) {
            messageManager.send(player, "invalid-amount", null);
            return true;
        }
        if (bet.compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(player, "invalid-amount", null);
            return true;
        }
        minesService.startGame(player, bet);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("10", "25", "50", "100");
        }
        return List.of();
    }
}
