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
import com.controlbro.besteconomy.gui.SellCommand;
import com.controlbro.besteconomy.gui.ShopCommand;
import com.controlbro.besteconomy.gui.ShopGuiService;
import com.controlbro.besteconomy.listener.PlayerJoinListener;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.shop.ShopAccountCommand;
import com.controlbro.besteconomy.shop.ShopAccountService;
import com.controlbro.besteconomy.shop.ShopAdminCommand;
import com.controlbro.besteconomy.shop.ShopDatabaseManager;
import com.controlbro.besteconomy.shop.ShopPendingCommandService;
import com.controlbro.besteconomy.shop.ShopTables;
import com.controlbro.besteconomy.vault.VaultEconomyProvider;
import java.math.BigDecimal;
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
    private ShopDatabaseManager shopDatabaseManager;
    private ShopAccountService shopAccountService;
    private ShopPendingCommandService shopPendingCommandService;
    private ShopGuiService shopGuiService;
    private ShopAccountCommand registeredShopAccountCommand;
    private BukkitTask autosaveTask;
    private BukkitTask shardRewardTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureConfigDefaults();
        messageManager = new MessageManager(this);
        currencyManager = new CurrencyManager(this);
        economyManager = new EconomyManager(currencyManager, new DataStore(this));
        commandHandler = new CurrencyCommandHandler(this, currencyManager, economyManager, messageManager);
        commandRegistrar = new CurrencyCommandRegistrar(this, currencyManager, commandHandler);

        registerCommands();
        commandRegistrar.registerAll();
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(economyManager), this);
        hookVault();
        startAutoSave();
        startShardRewardTask();
        startWebshopIntegration();
        startShopGui();
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (shardRewardTask != null) {
            shardRewardTask.cancel();
        }
        if (shopPendingCommandService != null) {
            shopPendingCommandService.stop();
        }
        economyManager.save();
        commandRegistrar.unregisterAll();
        HandlerList.unregisterAll(this);
    }

    public void reloadEverything() {
        reloadConfig();
        ensureConfigDefaults();
        messageManager.reload();
        currencyManager.reload();
        commandRegistrar.unregisterAll();
        registerCommands();
        commandRegistrar.registerAll();
        Bukkit.getOnlinePlayers().forEach(player -> economyManager.ensurePlayer(player.getUniqueId()));
        startAutoSave();
        startShardRewardTask();
        startWebshopIntegration();
        startShopGui();
    }

    private void registerCommands() {
        Currency defaultCurrency = currencyManager.getDefaultCurrency();
        Currency shardCurrency = currencyManager.getCurrency("shards");
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
        PluginCommand shardtop = getCommand("shardtop");
        if (shardtop != null && shardCurrency != null) {
            BaltopCommand shardTopCommand = new BaltopCommand(economyManager, messageManager, shardCurrency);
            shardtop.setExecutor(shardTopCommand);
            shardtop.setTabCompleter(shardTopCommand);
        }
        PluginCommand reload = getCommand("besteconomy");
        if (reload != null) {
            reload.setExecutor(new ReloadCommand(this, messageManager));
        }
    }


    private void registerShopCommands() {
        if (shopAccountService == null || shopPendingCommandService == null) {
            return;
        }
        PluginCommand shardShop = getCommand("shardshop");
        if (shardShop != null) {
            ShopAccountCommand shopAccountCommand = new ShopAccountCommand(this, shopAccountService, messageManager);
            shardShop.setExecutor(shopAccountCommand);
            shardShop.setTabCompleter(shopAccountCommand);
            Bukkit.getPluginManager().registerEvents(shopAccountCommand, this);
            registeredShopAccountCommand = shopAccountCommand;
        }
        PluginCommand shopAdmin = getCommand("shopadmin");
        if (shopAdmin != null) {
            ShopAdminCommand shopAdminCommand = new ShopAdminCommand(shopPendingCommandService, messageManager);
            shopAdmin.setExecutor(shopAdminCommand);
            shopAdmin.setTabCompleter(shopAdminCommand);
        }
    }

    private void startShopGui() {
        if (shopGuiService != null) {
            HandlerList.unregisterAll(shopGuiService);
        }
        shopGuiService = new ShopGuiService(this, economyManager, currencyManager, messageManager);
        Bukkit.getPluginManager().registerEvents(shopGuiService, this);
        PluginCommand shop = getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(shopGuiService, messageManager));
        }
        PluginCommand sell = getCommand("sell");
        if (sell != null) {
            sell.setExecutor(new SellCommand(shopGuiService, messageManager));
        }
    }

    private void startWebshopIntegration() {
        if (shopPendingCommandService != null) {
            shopPendingCommandService.stop();
        }
        if (registeredShopAccountCommand != null) {
            HandlerList.unregisterAll(registeredShopAccountCommand);
            registeredShopAccountCommand = null;
        }
        shopDatabaseManager = new ShopDatabaseManager(this);
        shopAccountService = new ShopAccountService(this, shopDatabaseManager);
        shopPendingCommandService = new ShopPendingCommandService(this, shopDatabaseManager);
        registerShopCommands();
        if (!getConfig().getBoolean("webshop.enabled", true)) {
            return;
        }
        if (!shopDatabaseManager.isEnabled()) {
            return;
        }
        new ShopTables(this, shopDatabaseManager).createAsync(shopPendingCommandService::start);
    }

    private void ensureConfigDefaults() {
        getConfig().addDefault("currency-symbol", "$");
        getConfig().addDefault("default-currency", "money");
        getConfig().addDefault("currencies.Money.symbol", "$");
        getConfig().addDefault("currencies.Money.command-alias", "money");
        getConfig().addDefault("currencies.Money.starting-balance", 0);
        getConfig().addDefault("currencies.Money.max-money", 10000000000000L);
        getConfig().addDefault("currencies.Money.min-money", 0);
        getConfig().addDefault("currencies.Shards.symbol", "✦");
        getConfig().addDefault("currencies.Shards.command-alias", "shards");
        getConfig().addDefault("currencies.Shards.starting-balance", 0);
        getConfig().addDefault("currencies.Shards.max-money", 10000000000000L);
        getConfig().addDefault("currencies.Shards.min-money", 0);
        if (!getConfig().getString("default-currency", "money").equalsIgnoreCase("money")) {
            getConfig().set("default-currency", "money");
        }
        getConfig().set("currency-symbol", "$");
        getConfig().set("currencies.Money.symbol", "$");
        getConfig().set("currencies.Money.command-alias", "money");
        getConfig().set("currencies.Shards.symbol", "✦");
        getConfig().set("currencies.Shards.command-alias", "shards");
        getConfig().addDefault("mysql.host", "localhost");
        getConfig().addDefault("mysql.port", 3306);
        getConfig().addDefault("mysql.database", "besteconomy");
        getConfig().addDefault("mysql.username", "CHANGE_THIS_USERNAME");
        getConfig().addDefault("mysql.password", "CHANGE_THIS_PASSWORD");
        getConfig().addDefault("mysql.use-ssl", false);
        getConfig().addDefault("mysql.connection-timeout-ms", 10000);
        getConfig().addDefault("webshop.enabled", true);
        getConfig().addDefault("webshop.pending-check-seconds", 60);
        getConfig().addDefault("webshop.max-commands-per-check", 50);
        getConfig().addDefault("webshop.api-key", "CHANGE_THIS_TO_A_LONG_RANDOM_SECRET");
        getConfig().options().copyDefaults(true);
        saveConfig();
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


    private void startShardRewardTask() {
        if (shardRewardTask != null) {
            shardRewardTask.cancel();
        }
        if (!getConfig().getBoolean("online-shard-reward.enabled", true)) {
            return;
        }
        Currency shardCurrency = currencyManager.getCurrency("shards");
        if (shardCurrency == null) {
            getLogger().warning("Unable to start online Shards reward task because the default currency is missing.");
            return;
        }
        long intervalSeconds = getConfig().getLong("online-shard-reward.interval-seconds", 60);
        if (intervalSeconds <= 0) {
            return;
        }
        BigDecimal rewardAmount = new BigDecimal(getConfig().getString("online-shard-reward.amount", "1"));
        shardRewardTask = Bukkit.getScheduler().runTaskTimer(this, () ->
            Bukkit.getOnlinePlayers().forEach(player ->
                economyManager.addBalance(player.getUniqueId(), shardCurrency, rewardAmount)),
            intervalSeconds * 20L, intervalSeconds * 20L);
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
