package com.controlbro.besteconomy.gui;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.ColorUtil;
import com.controlbro.besteconomy.util.NumberUtil;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopGuiService implements Listener {
    private static final int[] REMOVE_SLOTS = {11, 20, 29};
    private static final int[] ADD_SLOTS = {15, 24, 33};
    private static final int BUY_SLOT = 22;
    private static final int CANCEL_SLOT = 49;
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;
    private final File configFolder;
    private final Map<String, SectionConfig> sections = new HashMap<>();
    private YamlConfiguration homeConfig;
    private YamlConfiguration sellConfig;

    public ShopGuiService(JavaPlugin plugin, EconomyManager economyManager, CurrencyManager currencyManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
        this.configFolder = new File(plugin.getDataFolder(), "shopconfig");
        reload();
    }

    public void reload() {
        saveDefaultShopConfig("shopconfig/homepage.yml");
        saveDefaultShopConfig("shopconfig/sell.yml");
        saveDefaultShopConfig("shopconfig/sections/blocks.yml");
        homeConfig = YamlConfiguration.loadConfiguration(new File(configFolder, "homepage.yml"));
        sellConfig = YamlConfiguration.loadConfiguration(new File(configFolder, "sell.yml"));
        sections.clear();
    }

    public void openHome(Player player) {
        if (!player.hasPermission("shop.gui.use")) {
            messageManager.send(player, "no-permission", null);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new HomeHolder(), validSize(homeConfig.getInt("size", 27)), color(homeConfig.getString("title", "&8Money Shop")));
        if (homeConfig.getBoolean("fill-empty", true)) {
            fillEmpty(inventory);
        }
        ConfigurationSection sectionRoot = homeConfig.getConfigurationSection("sections");
        if (sectionRoot != null) {
            for (String key : sectionRoot.getKeys(false)) {
                ConfigurationSection section = sectionRoot.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                int slot = section.getInt("slot", -1);
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, item(section.getString("material", "CHEST"), section.getString("name", key), section.getStringList("lore")));
                    sections.put(key, loadSection(key, section.getString("file", "sections/" + key + ".yml")));
                }
            }
        }
        player.openInventory(inventory);
    }

    public void openSell(Player player) {
        if (!player.hasPermission("shop.sell.use")) {
            messageManager.send(player, "no-permission", null);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new SellHolder(), validSize(sellConfig.getInt("size", 54)), color(sellConfig.getString("title", "&8Sell Items")));
        updateSellButton(inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getInventory().getHolder() == null) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof HomeHolder) {
            handleHomeClick(event, player);
        } else if (holder instanceof SectionHolder sectionHolder) {
            handleSectionClick(event, player, sectionHolder.sectionConfig());
        } else if (holder instanceof BuyHolder buyHolder) {
            handleBuyClick(event, player, buyHolder);
        } else if (holder instanceof SellHolder sellHolder) {
            handleSellClick(event, sellHolder);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SellHolder sellHolder && !sellHolder.sold()) {
            returnSellItems((Player) event.getPlayer(), event.getInventory());
        }
    }

    private void handleHomeClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ConfigurationSection sectionRoot = homeConfig.getConfigurationSection("sections");
        if (sectionRoot == null) {
            return;
        }
        for (String key : sectionRoot.getKeys(false)) {
            ConfigurationSection section = sectionRoot.getConfigurationSection(key);
            if (section != null && event.getRawSlot() == section.getInt("slot", -1)) {
                SectionConfig sectionConfig = sections.computeIfAbsent(key, ignored -> loadSection(key, section.getString("file", "sections/" + key + ".yml")));
                openSection(player, sectionConfig);
                return;
            }
        }
    }

    private void handleSectionClick(InventoryClickEvent event, Player player, SectionConfig sectionConfig) {
        event.setCancelled(true);
        ShopItem clicked = sectionConfig.itemBySlot(event.getRawSlot());
        if (clicked != null) {
            openBuyMenu(player, clicked, 1);
        }
    }

    private void handleBuyClick(InventoryClickEvent event, Player player, BuyHolder holder) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        int amount = holder.amount();
        if (slot == REMOVE_SLOTS[0]) amount -= 1;
        if (slot == REMOVE_SLOTS[1]) amount -= 8;
        if (slot == REMOVE_SLOTS[2]) amount -= 16;
        if (slot == ADD_SLOTS[0]) amount += 1;
        if (slot == ADD_SLOTS[1]) amount += 8;
        if (slot == ADD_SLOTS[2]) amount += 16;
        amount = Math.max(1, Math.min(64, amount));
        if (slot == BUY_SLOT) {
            buy(player, holder.item(), amount);
            amount = holder.amount();
        } else if (slot == CANCEL_SLOT) {
            player.closeInventory();
            return;
        }
        openBuyMenu(player, holder.item(), amount);
    }

    private void handleSellClick(InventoryClickEvent event, SellHolder sellHolder) {
        int sellSlot = sellConfig.getInt("sell-button-slot", 49);
        if (event.getRawSlot() == sellSlot) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            BigDecimal total = calculateSellTotal(event.getInventory());
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                Currency money = getMoneyCurrency();
                economyManager.addBalance(player.getUniqueId(), money, total);
                sellHolder.sold(true);
                clearSellItems(event.getInventory());
                messageManager.send(player, "shop.sell-complete", Map.of("amount", NumberUtil.format(total)));
                Bukkit.getScheduler().runTask(plugin, () -> openSell(player));
                player.closeInventory();
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> updateSellButton(event.getInventory()));
    }

    private void openSection(Player player, SectionConfig sectionConfig) {
        Inventory inventory = Bukkit.createInventory(new SectionHolder(sectionConfig), sectionConfig.size(), color(sectionConfig.title()));
        for (ShopItem shopItem : sectionConfig.items()) {
            List<String> lore = new ArrayList<>(shopItem.lore());
            lore.replaceAll(line -> line.replace("{price}", NumberUtil.format(shopItem.price())));
            inventory.setItem(shopItem.slot(), item(shopItem.material().name(), shopItem.name(), lore));
        }
        player.openInventory(inventory);
    }

    private void openBuyMenu(Player player, ShopItem shopItem, int amount) {
        Inventory inventory = Bukkit.createInventory(new BuyHolder(shopItem, amount), 54, color("&8Buy " + shopItem.name()));
        inventory.setItem(REMOVE_SLOTS[0], item("RED_STAINED_GLASS_PANE", "&c-1", List.of("&7Decrease by 1")));
        inventory.setItem(REMOVE_SLOTS[1], item("RED_STAINED_GLASS_PANE", "&c-8", List.of("&7Decrease by 8")));
        inventory.setItem(REMOVE_SLOTS[2], item("RED_STAINED_GLASS_PANE", "&c-16", List.of("&7Decrease by 16")));
        inventory.setItem(ADD_SLOTS[0], item("GREEN_STAINED_GLASS_PANE", "&a+1", List.of("&7Increase by 1")));
        inventory.setItem(ADD_SLOTS[1], item("GREEN_STAINED_GLASS_PANE", "&a+8", List.of("&7Increase by 8")));
        inventory.setItem(ADD_SLOTS[2], item("GREEN_STAINED_GLASS_PANE", "&a+16", List.of("&7Increase by 16")));
        BigDecimal total = shopItem.price().multiply(BigDecimal.valueOf(amount));
        inventory.setItem(13, item(shopItem.material().name(), shopItem.name(), List.of("&7Amount: &e" + amount, "&7Total: &a$" + NumberUtil.format(total))));
        inventory.setItem(BUY_SLOT, item("LIME_STAINED_GLASS_PANE", "&aBuy", List.of("&7Click to buy", "&7Total: &a$" + NumberUtil.format(total))));
        inventory.setItem(CANCEL_SLOT, item("BARRIER", "&cCancel", List.of("&7Close this menu")));
        player.openInventory(inventory);
    }

    private void buy(Player player, ShopItem shopItem, int amount) {
        Currency money = getMoneyCurrency();
        BigDecimal total = shopItem.price().multiply(BigDecimal.valueOf(amount));
        if (economyManager.getAvailableToSpend(player.getUniqueId(), money).compareTo(total) < 0) {
            messageManager.send(player, "insufficient-funds", null);
            return;
        }
        economyManager.subtractBalance(player.getUniqueId(), money, total);
        player.getInventory().addItem(new ItemStack(shopItem.material(), amount)).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        messageManager.send(player, "shop.purchase-success", Map.of("amount", String.valueOf(amount), "item", shopItem.material().name(), "price", NumberUtil.format(total)));
    }

    private SectionConfig loadSection(String key, String filePath) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(configFolder, filePath));
        int size = validSize(config.getInt("size", 54));
        String title = config.getString("title", "&8" + key);
        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection itemRoot = config.getConfigurationSection("items");
        if (itemRoot != null) {
            for (String itemKey : itemRoot.getKeys(false)) {
                ConfigurationSection itemSection = itemRoot.getConfigurationSection(itemKey);
                if (itemSection == null) continue;
                Material material = material(itemSection.getString("material", "STONE"));
                BigDecimal price = new BigDecimal(itemSection.getString("price", "0"));
                items.add(new ShopItem(itemSection.getInt("slot", 0), material, itemSection.getString("name", material.name()), itemSection.getStringList("lore"), price));
            }
        }
        return new SectionConfig(key, title, size, items);
    }

    private void updateSellButton(Inventory inventory) {
        int slot = sellConfig.getInt("sell-button-slot", 49);
        if (slot >= 0 && slot < inventory.getSize()) {
            BigDecimal total = calculateSellTotal(inventory);
            inventory.setItem(slot, item("LIME_STAINED_GLASS_PANE", "&aSell", List.of("&7You will receive: &a$" + NumberUtil.format(total))));
        }
    }

    private BigDecimal calculateSellTotal(Inventory inventory) {
        BigDecimal total = BigDecimal.ZERO;
        int sellSlot = sellConfig.getInt("sell-button-slot", 49);
        ConfigurationSection values = sellConfig.getConfigurationSection("values");
        if (values == null) return total;
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == sellSlot) continue;
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack == null || itemStack.getType().isAir()) continue;
            BigDecimal value = new BigDecimal(values.getString(itemStack.getType().name(), "0"));
            total = total.add(value.multiply(BigDecimal.valueOf(itemStack.getAmount())));
        }
        return total;
    }

    private void clearSellItems(Inventory inventory) {
        int sellSlot = sellConfig.getInt("sell-button-slot", 49);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i != sellSlot) inventory.setItem(i, null);
        }
    }

    private void returnSellItems(Player player, Inventory inventory) {
        int sellSlot = sellConfig.getInt("sell-button-slot", 49);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == sellSlot) continue;
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack != null && !itemStack.getType().isAir()) {
                player.getInventory().addItem(itemStack).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }
    }

    private Currency getMoneyCurrency() {
        Currency money = currencyManager.getCurrency("money");
        return money == null ? currencyManager.getDefaultCurrency() : money;
    }

    private void saveDefaultShopConfig(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }

    private ItemStack item(String materialName, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material(materialName));
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(color(name));
        meta.lore(lore.stream().map(this::color).toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private Material material(String materialName) {
        Material material = Material.matchMaterial(materialName == null ? "STONE" : materialName);
        return material == null ? Material.STONE : material;
    }

    private void fillEmpty(Inventory inventory) {
        ItemStack filler = item("BLACK_STAINED_GLASS_PANE", " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler);
        }
    }

    private int validSize(int size) {
        if (size < 9) return 9;
        if (size > 54) return 54;
        return (size / 9) * 9;
    }

    private net.kyori.adventure.text.Component color(String text) {
        return ColorUtil.colorize(text == null ? "" : text);
    }

    private record HomeHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record SectionHolder(SectionConfig sectionConfig) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class BuyHolder implements InventoryHolder {
        private final ShopItem item;
        private final int amount;
        private BuyHolder(ShopItem item, int amount) { this.item = item; this.amount = amount; }
        @Override public Inventory getInventory() { return null; }
        private ShopItem item() { return item; }
        private int amount() { return amount; }
    }

    private static final class SellHolder implements InventoryHolder {
        private boolean sold;
        @Override public Inventory getInventory() { return null; }
        private boolean sold() { return sold; }
        private void sold(boolean sold) { this.sold = sold; }
    }

    private record SectionConfig(String key, String title, int size, List<ShopItem> items) {
        private ShopItem itemBySlot(int slot) {
            return items.stream().filter(item -> item.slot() == slot).findFirst().orElse(null);
        }
    }

    private record ShopItem(int slot, Material material, String name, List<String> lore, BigDecimal price) {
    }
}
