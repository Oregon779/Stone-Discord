package dev.stonediscord.plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;

/**
 * A single configured entry in a {@link LinkGuiDefinition}: an item shown at
 * a fixed slot that, when clicked, sends the player {@code link} as a
 * clickable chat message (see {@link LinkGuiListener}).
 */
public record LinkGuiItem(int slot, Material material, Component name, List<Component> lore, String link) {
}
