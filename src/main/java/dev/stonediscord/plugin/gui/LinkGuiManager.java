package dev.stonediscord.plugin.gui;

import dev.stonediscord.plugin.StoneDiscord;
import dev.stonediscord.plugin.manager.ConfigManager;
import dev.stonediscord.plugin.manager.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Generic, data-driven "link menu" GUI: an inventory built from a config.yml
 * section (enabled/title/rows/items), where clicking an item sends the
 * player a clickable chat message with that item's configured link.
 * <p>
 * /media and /social are both just differently-configured instances of this
 * same mechanism (keyed by "media"/"social" in config.yml, matching the
 * section name) - a future third menu only needs a new config section plus
 * a command registration, not new GUI code.
 */
public class LinkGuiManager {

    private static final List<String> MENU_KEYS = List.of("media", "social");
    private static final Set<Material> AIR_MATERIALS = Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

    private final StoneDiscord plugin;
    private volatile Map<String, LinkGuiDefinition> definitions = Map.of();

    public LinkGuiManager(StoneDiscord plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ConfigManager configManager = plugin.getConfigManager();
        MessageManager messageManager = plugin.getMessageManager();
        Logger logger = plugin.getLogger();

        Map<String, LinkGuiDefinition> newDefinitions = new LinkedHashMap<>();
        for (String menuKey : MENU_KEYS) {
            newDefinitions.put(menuKey, parseDefinition(menuKey, configManager.getSection(menuKey), messageManager, logger));
        }
        definitions = Map.copyOf(newDefinitions);
    }

    public LinkGuiDefinition get(String menuKey) {
        return definitions.get(menuKey);
    }

    /**
     * Opens the given menu for the player, or sends {@code disabledMessageKey}
     * instead if the menu is disabled (or missing) in config.yml.
     */
    public void open(Player player, String menuKey, String disabledMessageKey) {
        LinkGuiDefinition definition = get(menuKey);
        if (definition == null || !definition.enabled()) {
            plugin.getMessageManager().sendChat(player, disabledMessageKey, null);
            return;
        }

        LinkGuiHolder holder = new LinkGuiHolder(menuKey, definition);
        Inventory inventory = Bukkit.createInventory(holder, definition.rows() * 9, definition.title());
        holder.setInventory(inventory);
        for (LinkGuiItem item : definition.itemsBySlot().values()) {
            inventory.setItem(item.slot(), toItemStack(item));
        }
        player.openInventory(inventory);
    }

    private static ItemStack toItemStack(LinkGuiItem item) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(item.name());
        meta.lore(item.lore());
        stack.setItemMeta(meta);
        return stack;
    }

    static LinkGuiDefinition parseDefinition(String menuKey, ConfigurationSection section,
                                              MessageManager messageManager, Logger logger) {
        if (section == null) {
            return new LinkGuiDefinition(false, Component.text(menuKey), 3, Map.of());
        }

        boolean enabled = section.getBoolean("enabled", true);
        Component title = format(messageManager, section.getString("title", menuKey));
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 3)));

        Map<Integer, LinkGuiItem> items = new LinkedHashMap<>();
        for (Map<?, ?> raw : section.getMapList("items")) {
            LinkGuiItem item = parseItem(menuKey, raw, rows, messageManager, logger);
            if (item == null) {
                continue;
            }
            if (items.containsKey(item.slot())) {
                logger.warning(menuKey + ": two items configured for slot " + item.slot() + ", the later one wins.");
            }
            items.put(item.slot(), item);
        }
        return new LinkGuiDefinition(enabled, title, rows, Map.copyOf(items));
    }

    private static LinkGuiItem parseItem(String menuKey, Map<?, ?> raw, int rows,
                                          MessageManager messageManager, Logger logger) {
        Object slotValue = raw.get("slot");
        if (!(slotValue instanceof Number slotNumber)) {
            logger.warning(menuKey + ": an item is missing a numeric 'slot', skipped.");
            return null;
        }
        int slot = slotNumber.intValue();
        int maxSlot = rows * 9 - 1;
        if (slot < 0 || slot > maxSlot) {
            logger.warning(menuKey + ": item slot " + slot + " is outside the " + rows + "-row GUI (0-" + maxSlot + "), skipped.");
            return null;
        }

        Object materialValue = raw.get("material");
        Material material = materialValue != null ? Material.matchMaterial(String.valueOf(materialValue)) : null;
        // Plain enum comparison, not Material#isAir(): that method resolves a
        // live block-type registry lookup, which needs a running server and
        // isn't available in this plugin-independent parsing code (or in unit
        // tests - see LinkGuiManagerTest).
        if (material == null || AIR_MATERIALS.contains(material)) {
            logger.warning(menuKey + ": item at slot " + slot + " has an unknown or empty material ('" + materialValue + "'), skipped.");
            return null;
        }

        Object nameValue = raw.get("name");
        Component name = format(messageManager, nameValue != null ? String.valueOf(nameValue) : "");

        List<Component> lore = new ArrayList<>();
        if (raw.get("lore") instanceof List<?> loreLines) {
            for (Object line : loreLines) {
                lore.add(format(messageManager, String.valueOf(line)));
            }
        }

        Object linkValue = raw.get("link");
        String link = linkValue != null ? String.valueOf(linkValue).strip() : "";

        return new LinkGuiItem(slot, material, name, List.copyOf(lore), link);
    }

    private static Component format(MessageManager messageManager, String raw) {
        return messageManager.format(raw, null).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
