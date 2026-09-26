package dev.stonediscord.plugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises ConfigUpdater#mergeSection directly (via reflection) since it is
 * the actual config-migration algorithm and works on plain in-memory
 * YamlConfiguration objects - no JavaPlugin/server instance required. The
 * public ConfigUpdater#update wraps this with plugin.getResource(...) file
 * I/O, which needs a real or mocked plugin (see report: not covered here).
 */
class ConfigUpdaterTest {

    private static int mergeSection(ConfigurationSection defaults, ConfigurationSection current) throws Exception {
        Method method = ConfigUpdater.class.getDeclaredMethod("mergeSection", ConfigurationSection.class, ConfigurationSection.class);
        method.setAccessible(true);
        return (int) method.invoke(null, defaults, current);
    }

    private static YamlConfiguration yaml(String content) {
        return YamlConfiguration.loadConfiguration(new StringReader(content));
    }

    @Test
    void addsMissingTopLevelKey() throws Exception {
        YamlConfiguration defaults = yaml("language: en\nnew-option: true\n");
        YamlConfiguration current = yaml("language: de\n");

        int added = mergeSection(defaults, current);

        assertEquals(1, added);
        assertEquals(true, current.getBoolean("new-option"));
        // existing, admin-customized value must survive untouched
        assertEquals("de", current.getString("language"));
    }

    @Test
    void doesNotOverwriteExistingCustomizedValues() throws Exception {
        YamlConfiguration defaults = yaml("discord:\n  cooldown-seconds: 5\n  link: \"https://discord.gg/default\"\n");
        YamlConfiguration current = yaml("discord:\n  cooldown-seconds: 30\n  link: \"https://discord.gg/my-server\"\n");

        int added = mergeSection(defaults, current);

        assertEquals(0, added);
        assertEquals(30, current.getInt("discord.cooldown-seconds"));
        assertEquals("https://discord.gg/my-server", current.getString("discord.link"));
    }

    @Test
    void addsMissingKeyInsideExistingNestedSectionWithoutTouchingSiblings() throws Exception {
        YamlConfiguration defaults = yaml("discord:\n  enabled: true\n  cooldown-seconds: 5\n");
        YamlConfiguration current = yaml("discord:\n  enabled: false\n");

        int added = mergeSection(defaults, current);

        assertEquals(1, added);
        assertEquals(5, current.getInt("discord.cooldown-seconds"));
        // the admin's existing "enabled: false" must not be reset to the default (true)
        assertEquals(false, current.getBoolean("discord.enabled"));
    }

    @Test
    void addsWholeMissingNestedSection() throws Exception {
        YamlConfiguration defaults = yaml("update-checker:\n  enabled: true\n  check-interval-minutes: 60\n");
        YamlConfiguration current = yaml("language: en\n");

        int added = mergeSection(defaults, current);

        assertEquals(1, added);
        assertEquals(true, current.getBoolean("update-checker.enabled"));
        assertEquals(60, current.getInt("update-checker.check-interval-minutes"));
    }

    @Test
    void noChangesWhenCurrentAlreadyHasEverything() throws Exception {
        String yamlText = "language: en\ndiscord:\n  enabled: true\n  cooldown-seconds: 5\n";
        YamlConfiguration defaults = yaml(yamlText);
        YamlConfiguration current = yaml(yamlText);

        int added = mergeSection(defaults, current);

        assertEquals(0, added);
    }
}
