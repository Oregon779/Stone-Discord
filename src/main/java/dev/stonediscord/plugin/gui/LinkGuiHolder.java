package dev.stonediscord.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an open inventory as belonging to a {@link LinkGuiManager} menu, so
 * {@link LinkGuiListener} can recognize clicks in it and look up which
 * {@link LinkGuiItem} (and therefore which link) a clicked slot represents.
 */
public final class LinkGuiHolder implements InventoryHolder {

    private final String menuKey;
    private final LinkGuiDefinition definition;
    private Inventory inventory;

    LinkGuiHolder(String menuKey, LinkGuiDefinition definition) {
        this.menuKey = menuKey;
        this.definition = definition;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String menuKey() {
        return menuKey;
    }

    public LinkGuiDefinition definition() {
        return definition;
    }
}
