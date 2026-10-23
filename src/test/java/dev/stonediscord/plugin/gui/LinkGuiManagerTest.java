package dev.stonediscord.plugin.gui;

import dev.stonediscord.plugin.manager.MessageManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LinkGuiManager#parseDefinition/#parseItem are package-private and take
 * plain Bukkit config objects plus a plugin-independent MessageManager (see
 * MessageManagerTest) - no server/plugin instance needed, same pattern as
 * ConfigUpdaterTest.
 */
class LinkGuiManagerTest {

    private static final Logger LOGGER = Logger.getLogger("LinkGuiManagerTest");
    private static final MessageManager MESSAGE_MANAGER = new MessageManager(null);

    private static ConfigurationSection section(String yaml, String path) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml)).getConfigurationSection(path);
    }

    @Test
    void returnsDisabledEmptyDefinitionWhenSectionIsMissing() {
        LinkGuiDefinition definition = LinkGuiManager.parseDefinition("media", null, MESSAGE_MANAGER, LOGGER);

        assertFalse(definition.enabled());
        assertTrue(definition.itemsBySlot().isEmpty());
    }

    @Test
    void parsesEnabledTitleRowsAndItem() {
        String yaml = """
                media:
                  enabled: true
                  title: "<green>Media</green>"
                  rows: 3
                  items:
                    - slot: 10
                      material: PAPER
                      name: "<red>Click me</red>"
                      lore:
                        - "<gray>Line 1"
                        - "<gray>Line 2"
                      link: "https://example.com"
                """;

        LinkGuiDefinition definition = LinkGuiManager.parseDefinition("media", section(yaml, "media"), MESSAGE_MANAGER, LOGGER);

        assertTrue(definition.enabled());
        assertEquals(3, definition.rows());
        assertEquals(1, definition.itemsBySlot().size());

        LinkGuiItem item = definition.itemsBySlot().get(10);
        assertEquals(Material.PAPER, item.material());
        assertEquals("https://example.com", item.link());
        assertEquals(2, item.lore().size());
    }

    @Test
    void clampsRowsToTheOneToSixRange() {
        LinkGuiDefinition tooMany = LinkGuiManager.parseDefinition("media",
                section("media:\n  rows: 20\n", "media"), MESSAGE_MANAGER, LOGGER);
        assertEquals(6, tooMany.rows());

        LinkGuiDefinition tooFew = LinkGuiManager.parseDefinition("media",
                section("media:\n  rows: 0\n", "media"), MESSAGE_MANAGER, LOGGER);
        assertEquals(1, tooFew.rows());
    }

    @Test
    void skipsItemWithSlotOutsideTheGui() {
        String yaml = """
                media:
                  rows: 1
                  items:
                    - slot: 50
                      material: PAPER
                      link: "https://example.com"
                """;

        LinkGuiDefinition definition = LinkGuiManager.parseDefinition("media", section(yaml, "media"), MESSAGE_MANAGER, LOGGER);

        assertTrue(definition.itemsBySlot().isEmpty());
    }

    @Test
    void skipsItemMissingASlot() {
        String yaml = """
                media:
                  items:
                    - material: PAPER
                      link: "https://example.com"
                """;

        LinkGuiDefinition definition = LinkGuiManager.parseDefinition("media", section(yaml, "media"), MESSAGE_MANAGER, LOGGER);

        assertTrue(definition.itemsBySlot().isEmpty());
    }

    @Test
    void skipsItemWithUnknownMaterial() {
        String yaml = """
                media:
                  items:
                    - slot: 0
                      material: NOT_A_REAL_MATERIAL
                      link: "https://example.com"
                """;

        LinkGuiDefinition definition = LinkGuiManager.parseDefinition("media", section(yaml, "media"), MESSAGE_MANAGER, LOGGER);

        assertTrue(definition.itemsBySlot().isEmpty());
    }

    @Test
    void itemWithoutLinkParsesWithEmptyLinkInsteadOfNull() {
        String yaml = """
                media:
                  items:
                    - slot: 0
                      material: PAPER
                      name: "<gray>Just info</gray>"
                """;

        LinkGuiDefinition definition = LinkGuiManager.parseDefinition("media", section(yaml, "media"), MESSAGE_MANAGER, LOGGER);

        LinkGuiItem item = definition.itemsBySlot().get(0);
        assertEquals("", item.link());
    }

    @Test
    void laterItemWithDuplicateSlotOverwritesTheEarlierOne() {
        String yaml = """
                media:
                  items:
                    - slot: 4
                      material: PAPER
                      link: "https://first.example.com"
                    - slot: 4
                      material: BOOK
                      link: "https://second.example.com"
                """;

        LinkGuiDefinition definition = LinkGuiManager.parseDefinition("media", section(yaml, "media"), MESSAGE_MANAGER, LOGGER);

        assertEquals(1, definition.itemsBySlot().size());
        assertEquals("https://second.example.com", definition.itemsBySlot().get(4).link());
        assertEquals(Material.BOOK, definition.itemsBySlot().get(4).material());
    }

    @Test
    void multipleItemsAreAllParsedIndependently() {
        String yaml = """
                social:
                  rows: 3
                  items:
                    - slot: 10
                      material: PAPER
                      link: "https://youtube.com/@example"
                    - slot: 13
                      material: PAPER
                      link: "https://twitter.com/example"
                    - slot: 16
                      material: PAPER
                      link: "https://instagram.com/example"
                """;

        LinkGuiDefinition definition = LinkGuiManager.parseDefinition("social", section(yaml, "social"), MESSAGE_MANAGER, LOGGER);

        assertEquals(3, definition.itemsBySlot().size());
        assertEquals("https://youtube.com/@example", definition.itemsBySlot().get(10).link());
        assertEquals("https://twitter.com/example", definition.itemsBySlot().get(13).link());
        assertEquals("https://instagram.com/example", definition.itemsBySlot().get(16).link());
    }

    @Test
    void disabledFlagIsRespected() {
        LinkGuiDefinition definition = LinkGuiManager.parseDefinition("media",
                section("media:\n  enabled: false\n", "media"), MESSAGE_MANAGER, LOGGER);

        assertFalse(definition.enabled());
    }
}
