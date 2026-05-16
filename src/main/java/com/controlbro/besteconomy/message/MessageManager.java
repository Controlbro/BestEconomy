package com.controlbro.besteconomy.message;

import com.controlbro.besteconomy.placeholder.InternalPlaceholderService;
import com.controlbro.besteconomy.util.ColorUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MessageManager {
    private final JavaPlugin plugin;
    private FileConfiguration messages;
    private String prefix;
    private InternalPlaceholderService placeholderService;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void setPlaceholderService(InternalPlaceholderService placeholderService) {
        this.placeholderService = placeholderService;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        InputStream defaultsStream = plugin.getResource("messages.yml");
        if (defaultsStream != null) {
            try (InputStreamReader reader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                messages.setDefaults(defaults);
                messages.options().copyDefaults(true);
                if (messages instanceof YamlConfiguration yaml) {
                    yaml.save(file);
                }
            } catch (IOException ignored) {
                // ignored
            }
        }
        prefix = messages.getString("prefix", "");
    }

    public Component getMessage(String path, CommandSender sender, Map<String, String> placeholders) {
        String raw = messages.getString(path);
        if (raw == null) {
            return ColorUtil.colorize(prefix + "&cMissing message: " + path);
        }
        return ColorUtil.colorize(applyPlaceholders(prefix + raw, sender, placeholders));
    }

    public Component getMessage(String path, Map<String, String> placeholders) {
        return getMessage(path, null, placeholders);
    }

    public String applyPlaceholders(String message, CommandSender sender, Map<String, String> placeholders) {
        if (placeholderService == null) {
            String result = message == null ? "" : message;
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    result = result.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            return result;
        }
        return placeholderService.apply(message, sender, placeholders);
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(getMessage(path, sender, placeholders));
    }

    public void save() {
        if (messages instanceof YamlConfiguration yaml) {
            try {
                yaml.save(new File(plugin.getDataFolder(), "messages.yml"));
            } catch (IOException ignored) {
                // ignored
            }
        }
    }
}
