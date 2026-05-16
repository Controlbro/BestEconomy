package com.controlbro.besteconomy.visual;

import com.controlbro.besteconomy.placeholder.InternalPlaceholderService;
import com.controlbro.besteconomy.util.ColorUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class TabListService implements Listener {
    private static final String CONFIG_PATH = "tab.yml";
    private final JavaPlugin plugin;
    private final InternalPlaceholderService placeholderService;
    private YamlConfiguration config;
    private BukkitTask updateTask;

    public TabListService(JavaPlugin plugin, InternalPlaceholderService placeholderService) {
        this.plugin = plugin;
        this.placeholderService = placeholderService;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), CONFIG_PATH);
        if (!file.exists()) {
            plugin.saveResource(CONFIG_PATH, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        stop();
        if (!config.getBoolean("enabled", true)) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long intervalTicks = Math.max(1L, config.getLong("update-interval-ticks", 40L));
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 1L, intervalTicks);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(event.getPlayer()), 1L);
    }

    private void update(Player player) {
        Map<String, String> placeholders = Map.of(
            "tab_prefix", prefixFor(player),
            "players_online_display", playersOnlineDisplay(player));
        Component header = ColorUtil.colorize(apply(player, String.join("\n", config.getStringList("header")), placeholders));
        Component footer = ColorUtil.colorize(apply(player, String.join("\n", config.getStringList("footer")), placeholders));
        player.sendPlayerListHeaderAndFooter(header, footer);
        if (config.getBoolean("player-name.enabled", true)) {
            String format = config.getString("player-name.format", "{tab_prefix}{player}");
            player.playerListName(ColorUtil.colorize(apply(player, format, placeholders)));
        }
    }

    private String prefixFor(Player player) {
        List<PrefixEntry> entries = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("prefixes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                entries.add(new PrefixEntry(entry.getString("permission", ""), entry.getString("prefix", ""), entry.getInt("priority", 0)));
            }
        }
        entries.sort(Comparator.comparingInt(PrefixEntry::priority).reversed());
        for (PrefixEntry entry : entries) {
            String permission = entry.permission() == null ? "" : entry.permission();
            if (permission.isBlank() || player.hasPermission(permission)) {
                return entry.prefix() == null ? "" : entry.prefix();
            }
        }
        String defaultPrefix = config.getString("default-prefix", "");
        return defaultPrefix == null ? "" : defaultPrefix;
    }

    private String playersOnlineDisplay(Player player) {
        if (!config.getBoolean("players-online.enabled", true)) {
            return "";
        }
        String format = config.getString("players-online.format", "{players_online}/{max_players}");
        return placeholderService.apply(format, player, null);
    }

    private String apply(Player player, String text, Map<String, String> extraPlaceholders) {
        Map<String, String> placeholders = new HashMap<>();
        if (extraPlaceholders != null) {
            placeholders.putAll(extraPlaceholders);
        }
        return placeholderService.apply(text, player, placeholders);
    }

    private record PrefixEntry(String permission, String prefix, int priority) {
    }
}
