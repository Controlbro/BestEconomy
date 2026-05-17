package com.controlbro.besteconomy.blackjack;

import com.controlbro.besteconomy.message.MessageManager;
import java.math.BigDecimal;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BlackjackCommand implements CommandExecutor, TabCompleter {
    private final BlackjackService blackjackService;
    private final MessageManager messageManager;

    public BlackjackCommand(BlackjackService blackjackService, MessageManager messageManager) {
        this.blackjackService = blackjackService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "shop.players-only", null);
            return true;
        }
        if (!player.hasPermission("besteconomy.blackjack.use")) {
            messageManager.send(player, "no-permission", null);
            return true;
        }
        if (blackjackService.hasActiveGame(player)) {
            blackjackService.openGame(player);
            return true;
        }
        if (args.length != 1) {
            messageManager.send(player, "blackjack.usage", null);
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
        blackjackService.startGame(player, bet);
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
