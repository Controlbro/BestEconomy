package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class EcoCommand implements CommandExecutor, TabCompleter {
    private final CurrencyCommandHandler handler;
    private final Currency currency;

    public EcoCommand(CurrencyCommandHandler handler, Currency currency) {
        this.handler = handler;
        this.currency = currency;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handler.handle(sender, currency, label, args);
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return CurrencyTabCompleter.complete(sender, currency, args);
    }
}
