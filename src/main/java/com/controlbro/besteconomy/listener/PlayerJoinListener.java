package com.controlbro.besteconomy.listener;

import com.controlbro.besteconomy.data.EconomyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    private final EconomyManager economyManager;

    public PlayerJoinListener(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economyManager.ensurePlayer(event.getPlayer().getUniqueId());
    }
}
