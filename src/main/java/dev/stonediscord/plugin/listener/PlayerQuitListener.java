package dev.stonediscord.plugin.listener;

import dev.stonediscord.plugin.StoneDiscord;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final StoneDiscord plugin;

    public PlayerQuitListener(StoneDiscord plugin) {
        this.plugin = plugin;
    }

    // MONITOR-Prioritaet + kein State-Zugriff auf den Spieler selbst: diese
    // Bereinigung darf nie einen anderen Listener beeinflussen und muss nicht
    // vor anderen Plugins laufen. Reine O(1)-Map-Operation, kein messbarer
    // Overhead auch bei 250+ gleichzeitigen Quits (z.B. Server-Restart).
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getCooldownManager().remove(event.getPlayer().getUniqueId());
    }
}
