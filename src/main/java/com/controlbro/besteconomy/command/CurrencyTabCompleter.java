package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CurrencyTabCompleter {
    private CurrencyTabCompleter() {
    }

    public static List<String> completeEco(CommandSender sender, Currency currency, String[] args) {
        if (args.length == 1) {
            return List.of("give", "take", "reset", "set");
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3) {
            return List.of("money", "shards");
        }
        return List.of();
    }

    public static List<String> completeBalance(CommandSender sender, String[] args) {
        return List.of();
    }
}
