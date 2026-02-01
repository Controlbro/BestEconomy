package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CurrencyTabCompleter {
    private CurrencyTabCompleter() {
    }

    public static List<String> complete(CommandSender sender, Currency currency, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("give");
            completions.add("take");
            completions.add("reset");
            completions.add("pay");
            completions.add("balance");
            return completions;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("take") || sub.equals("reset") || sub.equals("pay")
                || sub.equals("balance")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
        }
        return List.of();
    }
}
