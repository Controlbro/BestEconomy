package com.controlbro.besteconomy.mines;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.ColorUtil;
import com.controlbro.besteconomy.util.NumberUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class MinesService implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int CASH_OUT_SLOT = 49;
    private static final int GRID_SIZE = 25;
    private static final int[] GRID_SLOTS = {
        2, 3, 4, 5, 6,
        11, 12, 13, 14, 15,
        20, 21, 22, 23, 24,
        29, 30, 31, 32, 33,
        38, 39, 40, 41, 42
    };

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;
    private final Map<UUID, MinesGame> games = new java.util.HashMap<>();

    public MinesService(JavaPlugin plugin, EconomyManager economyManager, CurrencyManager currencyManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    public boolean hasActiveGame(Player player) {
        return games.containsKey(player.getUniqueId());
    }

    public void refundActiveGames() {
        Currency shards = currencyManager.getCurrency("shards");
        if (shards == null) {
            games.clear();
            return;
        }
        for (MinesGame game : games.values()) {
            economyManager.addBalance(game.player, shards, game.bet);
        }
        games.clear();
    }

    public void startGame(Player player, BigDecimal bet) {
        Currency shards = currencyManager.getCurrency("shards");
        if (shards == null) {
            messageManager.send(player, "mines.shards-missing", null);
            return;
        }
        if (economyManager.getAvailableToSpend(player.getUniqueId(), shards).compareTo(bet) < 0) {
            messageManager.send(player, "insufficient-shards", null);
            return;
        }
        economyManager.subtractBalance(player.getUniqueId(), shards, bet);
        MinesGame game = new MinesGame(player.getUniqueId(), bet, mineSlots());
        games.put(player.getUniqueId(), game);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.1F);
        messageManager.send(player, "mines.started", Map.of("bet", NumberUtil.format(bet)));
        openGame(player);
    }

    public void openGame(Player player) {
        MinesGame game = games.get(player.getUniqueId());
        if (game == null) {
            messageManager.send(player, "mines.usage", null);
            return;
        }
        player.openInventory(inventory(game));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MinesHolder)) {
            return;
        }
        event.setCancelled(true);
        MinesGame game = games.get(player.getUniqueId());
        if (game == null) {
            player.closeInventory();
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == CASH_OUT_SLOT) {
            cashOut(player, game);
            return;
        }
        int gridIndex = gridIndex(rawSlot);
        if (gridIndex < 0 || game.revealed.contains(gridIndex)) {
            return;
        }
        if (game.mines.contains(gridIndex)) {
            lose(player, game, gridIndex);
            return;
        }
        revealDiamond(player, game, gridIndex);
    }

    private void revealDiamond(Player player, MinesGame game, int gridIndex) {
        game.revealed.add(gridIndex);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.7F);
        if (game.revealed.size() >= GRID_SIZE - game.mines.size()) {
            cashOut(player, game);
            return;
        }
        player.openInventory(inventory(game));
    }

    private void lose(Player player, MinesGame game, int mineIndex) {
        games.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.8F);
        Inventory inventory = inventory(game);
        inventory.setItem(GRID_SLOTS[mineIndex], item(Material.TNT, "&cBOOM!", List.of("&7You lost &5✦" + NumberUtil.format(game.bet) + "&7.")));
        player.openInventory(inventory);
        messageManager.send(player, "mines.lost", Map.of("bet", NumberUtil.format(game.bet)));
        broadcast("mines.announce-loss", Map.of("player", player.getName(), "bet", NumberUtil.format(game.bet)));
    }

    private void cashOut(Player player, MinesGame game) {
        games.remove(player.getUniqueId());
        Currency shards = currencyManager.getCurrency("shards");
        if (shards == null) {
            return;
        }
        BigDecimal payout = payout(game);
        economyManager.addBalance(player.getUniqueId(), shards, payout);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        player.closeInventory();
        messageManager.send(player, "mines.cash-out", Map.of(
            "amount", NumberUtil.format(payout),
            "multiplier", multiplier(game).toPlainString()));
        broadcast("mines.announce-win", Map.of(
            "player", player.getName(),
            "amount", NumberUtil.format(payout),
            "multiplier", multiplier(game).toPlainString()));
    }

    private Inventory inventory(MinesGame game) {
        Inventory inventory = Bukkit.createInventory(new MinesHolder(), INVENTORY_SIZE, color("&8Mines"));
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (game.revealed.contains(i)) {
                inventory.setItem(GRID_SLOTS[i], item(Material.DIAMOND, "&bDiamond", List.of("&7Safe pick!")));
            } else {
                inventory.setItem(GRID_SLOTS[i], item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Hidden Tile", List.of("&7Click to reveal a diamond or TNT.")));
            }
        }
        inventory.setItem(CASH_OUT_SLOT, cashOutItem(game));
        return inventory;
    }

    private ItemStack cashOutItem(MinesGame game) {
        BigDecimal multiplier = multiplier(game);
        BigDecimal payout = payout(game);
        return item(Material.EMERALD_BLOCK, "&aCash Out", List.of(
            "&7Bet: &5✦" + NumberUtil.format(game.bet),
            "&7Safe Picks: &e" + game.revealed.size(),
            "&7Total: &5✦" + NumberUtil.format(payout) + " &7(x" + multiplier.toPlainString() + ")",
            "&aClick to cash out."));
    }

    private Set<Integer> mineSlots() {
        int mineCount = Math.max(1, Math.min(GRID_SIZE - 1, plugin.getConfig().getInt("mines.mine-count", 5)));
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < GRID_SIZE; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots);
        return new HashSet<>(slots.subList(0, mineCount));
    }

    private BigDecimal multiplier(MinesGame game) {
        BigDecimal step = new BigDecimal(plugin.getConfig().getString("mines.multiplier-increase", "0.25"));
        return BigDecimal.ONE.add(step.multiply(BigDecimal.valueOf(game.revealed.size()))).setScale(2, RoundingMode.DOWN).stripTrailingZeros();
    }

    private BigDecimal payout(MinesGame game) {
        return game.bet.multiply(multiplier(game)).setScale(2, RoundingMode.DOWN).stripTrailingZeros();
    }

    private int gridIndex(int slot) {
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (GRID_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private void broadcast(String path, Map<String, String> placeholders) {
        Component message = messageManager.getMessage(path, null, placeholders);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(color(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(color(line));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private Component color(String text) {
        return ColorUtil.colorize(text == null ? "" : text);
    }

    private record MinesHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class MinesGame {
        private final UUID player;
        private final BigDecimal bet;
        private final Set<Integer> mines;
        private final Set<Integer> revealed = new HashSet<>();

        private MinesGame(UUID player, BigDecimal bet, Set<Integer> mines) {
            this.player = player;
            this.bet = bet;
            this.mines = mines;
        }
    }
}
