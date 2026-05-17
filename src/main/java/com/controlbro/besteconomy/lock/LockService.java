package com.controlbro.besteconomy.lock;

import com.controlbro.besteconomy.message.MessageManager;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public class LockService implements Listener {
    private final MessageManager messageManager;
    private final File file;
    private final Map<String, LockEntry> locks = new HashMap<>();
    private final Set<UUID> lockMode = new HashSet<>();

    public LockService(JavaPlugin plugin, MessageManager messageManager) {
        this.messageManager = messageManager;
        this.file = new File(plugin.getDataFolder(), "locks.yml");
        load();
    }

    public void toggleLockMode(Player player) {
        if (lockMode.remove(player.getUniqueId())) {
            messageManager.send(player, "lock.mode-disabled", null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 0.8F);
            return;
        }
        lockMode.add(player.getUniqueId());
        messageManager.send(player, "lock.mode-enabled", null);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("locks");
        for (Map.Entry<String, LockEntry> entry : locks.entrySet()) {
            ConfigurationSection section = root.createSection(entry.getKey());
            section.set("owner", entry.getValue().owner.toString());
            section.set("owner-name", entry.getValue().ownerName);
        }
        try {
            config.save(file);
        } catch (IOException ignored) {
            // ignored
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (!isLockable(block)) {
            return;
        }
        String key = key(block);
        LockEntry lock = locks.get(key);
        if (lockMode.contains(player.getUniqueId())) {
            event.setCancelled(true);
            if (lock == null) {
                locks.put(key, new LockEntry(player.getUniqueId(), player.getName()));
                save();
                messageManager.send(player, "lock.locked", null);
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0F, 1.2F);
                return;
            }
            if (lock.owner.equals(player.getUniqueId())) {
                messageManager.send(player, "lock.already-owned", null);
                return;
            }
            messageManager.send(player, "lock.already-locked", Map.of("owner", lock.ownerName));
            return;
        }
        if (lock != null && !lock.owner.equals(player.getUniqueId()) && !player.hasPermission("besteconomy.lock.bypass")) {
            event.setCancelled(true);
            messageManager.send(player, "lock.no-access", Map.of("owner", lock.ownerName));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0F, 1.0F);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isLockable(block)) {
            return;
        }
        String key = key(block);
        LockEntry lock = locks.get(key);
        if (lock == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!lock.owner.equals(player.getUniqueId()) && !player.hasPermission("besteconomy.lock.bypass")) {
            event.setCancelled(true);
            messageManager.send(player, "lock.no-break", Map.of("owner", lock.ownerName));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0F, 1.0F);
            return;
        }
        locks.remove(key);
        save();
        messageManager.send(player, "lock.removed", null);
    }

    private boolean isLockable(Block block) {
        if (block.getState() instanceof Container) {
            return true;
        }
        Material type = block.getType();
        String name = type.name();
        return name.endsWith("_DOOR")
            || name.endsWith("_TRAPDOOR")
            || name.endsWith("_FENCE_GATE")
            || name.endsWith("_SHULKER_BOX")
            || type == Material.CHEST
            || type == Material.TRAPPED_CHEST
            || type == Material.ENDER_CHEST
            || type == Material.FURNACE
            || type == Material.BLAST_FURNACE
            || type == Material.SMOKER
            || type == Material.BARREL
            || type == Material.HOPPER
            || type == Material.DISPENSER
            || type == Material.DROPPER
            || type == Material.BREWING_STAND;
    }

    private String key(Block block) {
        Location location = canonicalBlock(block).getLocation();
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    private Block canonicalBlock(Block block) {
        if (block.getBlockData() instanceof Bisected bisected && bisected.getHalf() == Bisected.Half.TOP) {
            return block.getRelative(BlockFace.DOWN);
        }
        return block;
    }

    private void load() {
        locks.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("locks");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String owner = section.getString("owner");
            if (owner == null) {
                continue;
            }
            try {
                locks.put(key, new LockEntry(UUID.fromString(owner), section.getString("owner-name", "Unknown")));
            } catch (IllegalArgumentException ignored) {
                // ignored
            }
        }
    }

    private record LockEntry(UUID owner, String ownerName) {
    }
}
