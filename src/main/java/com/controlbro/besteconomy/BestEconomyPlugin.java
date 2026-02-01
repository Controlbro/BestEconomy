package com.controlbro.besteconomy;

import com.controlbro.besteconomy.command.BaltopCommand;
import com.controlbro.besteconomy.command.BalanceCommand;
import com.controlbro.besteconomy.command.CurrencyCommandHandler;
import com.controlbro.besteconomy.command.CurrencyCommandRegistrar;
import com.controlbro.besteconomy.command.EcoCommand;
import com.controlbro.besteconomy.command.PayCommand;
import com.controlbro.besteconomy.command.ReloadCommand;
import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.DataStore;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.listener.PlayerJoinListener;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.vault.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class BestEconomyPlugin extends JavaPlugin {
    private CurrencyManager currencyManager;
    private EconomyManager economyManager;
    private MessageManager messageManager;
    private CurrencyCommandHandler commandHandler;
    private CurrencyCommandRegistrar commandRegistrar;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messageManager = new MessageManager(this);
        currencyManager = new CurrencyManager(this);
        economyManager = new EconomyManager(currencyManager, new DataStore(this));
        commandHandler = new CurrencyCommandHandler(this, economyManager, messageManager);
        commandRegistrar = new CurrencyCommandRegistrar(this, currencyManager, commandHandler);

        registerCommands();
        commandRegistrar.registerAll();
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(economyManager), this);
        hookVault();
        startAutoSave();
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        economyManager.save();
        commandRegistrar.unregisterAll();
        HandlerList.unregisterAll(this);
    }

    public void reloadEverything() {
        reloadConfig();
        messageManager.reload();
        currencyManager.reload();
        commandRegistrar.unregisterAll();
        registerCommands();
        commandRegistrar.registerAll();
        Bukkit.getOnlinePlayers().forEach(player -> economyManager.ensurePlayer(player.getUniqueId()));
        startAutoSave();
    }

    private void registerCommands() {
        Currency defaultCurrency = currencyManager.getDefaultCurrency();
        if (defaultCurrency == null) {
            getLogger().severe("Default currency not found in config.yml.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        PluginCommand eco = getCommand("eco");
        if (eco != null) {
            EcoCommand ecoCommand = new EcoCommand(commandHandler, defaultCurrency);
            eco.setExecutor(ecoCommand);
            eco.setTabCompleter(ecoCommand);
        }
        PluginCommand balance = getCommand("balance");
        if (balance != null) {
            BalanceCommand balanceCommand = new BalanceCommand(economyManager, messageManager, defaultCurrency);
            balance.setExecutor(balanceCommand);
            balance.setTabCompleter(balanceCommand);
        }
        PluginCommand pay = getCommand("pay");
        if (pay != null) {
            PayCommand payCommand = new PayCommand(this, economyManager, messageManager, defaultCurrency);
            pay.setExecutor(payCommand);
            pay.setTabCompleter(payCommand);
        }
        PluginCommand baltop = getCommand("baltop");
        if (baltop != null) {
            BaltopCommand baltopCommand = new BaltopCommand(economyManager, messageManager, defaultCurrency);
            baltop.setExecutor(baltopCommand);
            baltop.setTabCompleter(baltopCommand);
        }
        PluginCommand reload = getCommand("besteconomy");
        if (reload != null) {
            reload.setExecutor(new ReloadCommand(this, messageManager));
        }
    }

    private void startAutoSave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long intervalSeconds = getConfig().getLong("auto-save-interval-seconds", 300);
        if (intervalSeconds <= 0) {
            return;
        }
        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this,
            economyManager::save, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    private void hookVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        Currency defaultCurrency = currencyManager.getDefaultCurrency();
        if (defaultCurrency == null) {
            return;
        }
        Economy provider = new VaultEconomyProvider(economyManager, defaultCurrency);
        Bukkit.getServicesManager().register(Economy.class, provider, this, ServicePriority.High);
    }
}
