package com.controlbro.besteconomy.coinflip;

import com.controlbro.besteconomy.message.MessageManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class CoinflipCommand implements CommandExecutor, TabCompleter {
    private final CoinflipService coinflipService;
    private final MessageManager messageManager;

    public CoinflipCommand(CoinflipService coinflipService, MessageManager messageManager) {
        this.coinflipService = coinflipService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "shop.players-only", null);
            return true;
        }
        if (!player.hasPermission("besteconomy.coinflip.use")) {
            messageManager.send(player, "no-permission", null);
            return true;
        }
        if (args.length == 0) {
            messageManager.send(player, "coinflip.usage", null);
            return true;
        }
        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("create")) {
            create(player, args);
            return true;
        }
        if (subCommand.equals("join")) {
            join(player, args);
            return true;
        }
        if (subCommand.equals("cancel")) {
            coinflipService.cancel(player);
            return true;
        }
        if (subCommand.equals("list")) {
            coinflipService.list(player);
            return true;
        }
        messageManager.send(player, "coinflip.usage", null);
        return true;
    }

    private void create(Player player, String[] args) {
        if (args.length != 2) {
            messageManager.send(player, "coinflip.create-usage", null);
            return;
        }
        BigDecimal bet = parseAmount(player, args[1]);
        if (bet == null) {
            return;
        }
        coinflipService.create(player, bet);
    }

    private void join(Player player, String[] args) {
        if (coinflipService.isAwaitingCreatorPick(player)) {
            if (args.length != 2) {
                messageManager.send(player, "coinflip.pick-usage", null);
                return;
            }
            CoinflipGame.Side side = CoinflipGame.Side.parse(args[1]);
            if (side == null) {
                messageManager.send(player, "coinflip.invalid-side", null);
                return;
            }
            coinflipService.pickAndStart(player, side);
            return;
        }
        if (args.length > 2) {
            messageManager.send(player, "coinflip.join-usage", null);
            return;
        }
        Player creator = null;
        if (args.length == 2) {
            creator = Bukkit.getPlayerExact(args[1]);
            if (creator == null) {
                messageManager.send(player, "player-not-found", null);
                return;
            }
        }
        coinflipService.join(player, creator);
    }

    private BigDecimal parseAmount(Player player, String raw) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            messageManager.send(player, "invalid-amount", null);
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            messageManager.send(player, "invalid-amount", null);
            return null;
        }
        return amount;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "join", "cancel", "list");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return List.of("10", "25", "50", "100");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            if (sender instanceof Player player && coinflipService.isAwaitingCreatorPick(player)) {
                return List.of("heads", "tails");
            }
            List<String> names = new ArrayList<>();
            for (Player creator : coinflipService.openCreators()) {
                names.add(creator.getName());
            }
            return names;
        }
        return List.of();
    }
}
