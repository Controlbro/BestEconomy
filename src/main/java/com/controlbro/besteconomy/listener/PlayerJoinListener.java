package com.controlbro.besteconomy.listener;

import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    private final EconomyManager economyManager;
    private final MessageManager messageManager;

    public PlayerJoinListener(EconomyManager economyManager, MessageManager messageManager) {
        this.economyManager = economyManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economyManager.ensurePlayer(event.getPlayer().getUniqueId());
        if (!messageManager.getBoolean("join.enabled", true)) {
            return;
        }
        String messagePath = event.getPlayer().hasPlayedBefore() ? "join.message" : "join.first-time-message";
        event.joinMessage(messageManager.getMessage(messagePath, event.getPlayer(), null));
    }
}
