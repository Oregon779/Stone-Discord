package dev.stonediscord.plugin.gui;

import dev.stonediscord.plugin.StoneDiscord;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;

public class LinkGuiListener implements Listener {

    private final StoneDiscord plugin;

    public LinkGuiListener(StoneDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LinkGuiHolder holder)) {
            return;
        }
        // Always cancel inside one of our menus - these items are display-only
        // and must never be taken, moved or shift-clicked into the player's
        // own inventory.
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return; // click landed in the player's own inventory while the menu is open
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        LinkGuiItem item = holder.definition().itemsBySlot().get(event.getSlot());
        if (item == null || item.link().isEmpty()) {
            return;
        }

        player.closeInventory();
        plugin.getMessageManager().sendChat(player, holder.menuKey() + ".link-sent", Map.of("link", item.link()));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LinkGuiHolder) {
            event.setCancelled(true);
        }
    }
}
