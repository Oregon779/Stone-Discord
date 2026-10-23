package dev.stonediscord.plugin.gui;

import net.kyori.adventure.text.Component;

import java.util.Map;

/**
 * The fully parsed, ready-to-render state of one configurable link menu
 * (e.g. "media" or "social"), built once per config load/reload by
 * {@link LinkGuiManager}.
 */
public record LinkGuiDefinition(boolean enabled, Component title, int rows, Map<Integer, LinkGuiItem> itemsBySlot) {
}
