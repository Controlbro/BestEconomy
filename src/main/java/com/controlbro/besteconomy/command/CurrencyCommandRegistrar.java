package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;

public class CurrencyCommandRegistrar {
    private final JavaPlugin plugin;
    private final CurrencyManager currencyManager;
    private final CurrencyCommandHandler handler;
    private final Map<String, Command> registered = new HashMap<>();

    public CurrencyCommandRegistrar(JavaPlugin plugin, CurrencyManager currencyManager, CurrencyCommandHandler handler) {
        this.plugin = plugin;
        this.currencyManager = currencyManager;
        this.handler = handler;
    }

    public void registerAll() {
        CommandMap commandMap = getCommandMap();
        if (commandMap == null) {
            return;
        }
        for (Currency currency : currencyManager.getCurrencies()) {
            String alias = currency.getCommandAlias();
            if (alias.equalsIgnoreCase("eco") || alias.equalsIgnoreCase("economy")) {
                continue;
            }
            String lower = alias.toLowerCase();
            if (commandMap.getCommand(lower) != null) {
                continue;
            }
            Command command = new DynamicCurrencyCommand(currency, handler, plugin.getName());
            commandMap.register(plugin.getName(), command);
            registered.put(lower, command);
        }
    }

    public void unregisterAll() {
        CommandMap commandMap = getCommandMap();
        if (!(commandMap instanceof SimpleCommandMap simpleCommandMap)) {
            return;
        }
        try {
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(simpleCommandMap);
            for (Map.Entry<String, Command> entry : registered.entrySet()) {
                knownCommands.remove(entry.getKey());
                knownCommands.remove(plugin.getName().toLowerCase() + ":" + entry.getKey());
            }
        } catch (ReflectiveOperationException ignored) {
            // ignored
        }
        registered.clear();
    }

    private CommandMap getCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            return (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static class DynamicCurrencyCommand extends Command {
        private final Currency currency;
        private final CurrencyCommandHandler handler;

        protected DynamicCurrencyCommand(Currency currency, CurrencyCommandHandler handler, String pluginName) {
            super(currency.getCommandAlias());
            this.currency = currency;
            this.handler = handler;
            setDescription("Economy command for currency " + currency.getName());
            setPermissionMessage("You do not have permission.");
            setLabel(currency.getCommandAlias());
            setUsage("/" + currency.getCommandAlias());
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return handler.handle(sender, currency, commandLabel, args);
        }

        @Override
        public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return CurrencyTabCompleter.completeBalance(sender, args);
        }
    }
}
