package dev.stonediscord.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises ConfigUpdater#mergeAddingMissingKeys (the comment-preserving
 * line merger) and #applyRenames directly - both are package-private and
 * work on plain in-memory YamlConfiguration/line-list data, no
 * JavaPlugin/server instance required. The public ConfigUpdater#update
 * wraps this with plugin.getResource(...)/file I/O, which needs a real or
 * mocked plugin (see repo WIKI.md "Bekannte Einschraenkungen").
 */
class ConfigUpdaterTest {

    private static YamlConfiguration yaml(String text) {
        return YamlConfiguration.loadConfiguration(new StringReader(text));
    }

    private static List<String> lines(String text) {
        return text.lines().toList();
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    void addsMissingTopLevelKeyAndCarriesItsComment() {
        String defaultText = """
                language: en

                # Whether /discord is usable at all.
                discord-enabled: true
                """;
        String currentText = """
                language: de
                """;

        ConfigUpdater.MergeOutcome outcome = ConfigUpdater.mergeAddingMissingKeys(
                yaml(defaultText), yaml(currentText), lines(defaultText), lines(currentText));

        assertEquals(1, outcome.addedKeys());
        String merged = joined(outcome.mergedLines());
        assertTrue(merged.contains("# Whether /discord is usable at all."), "comment above the new key must be carried over");
        assertTrue(merged.contains("discord-enabled: true"));
        // the admin's existing, customized value must survive untouched
        assertTrue(merged.contains("language: de"));
        assertFalse(merged.contains("language: en"));
    }

    @Test
    void doesNotTouchAnyLineWhenNothingIsMissing() {
        String text = """
                language: en
                discord:
                  enabled: true
                  cooldown-seconds: 5
                """;

        ConfigUpdater.MergeOutcome outcome = ConfigUpdater.mergeAddingMissingKeys(
                yaml(text), yaml(text), lines(text), lines(text));

        assertEquals(0, outcome.addedKeys());
        assertEquals(lines(text), outcome.mergedLines());
    }

    @Test
    void doesNotOverwriteCustomizedExistingValueInsideExistingSection() {
        String defaultText = """
                discord:
                  enabled: true
                  cooldown-seconds: 5
                """;
        String currentText = """
                discord:
                  enabled: true
                  cooldown-seconds: 30
                """;

        ConfigUpdater.MergeOutcome outcome = ConfigUpdater.mergeAddingMissingKeys(
                yaml(defaultText), yaml(currentText), lines(defaultText), lines(currentText));

        assertEquals(0, outcome.addedKeys());
        YamlConfiguration result = yaml(joined(outcome.mergedLines()));
        assertEquals(30, result.getInt("discord.cooldown-seconds"));
    }

    @Test
    void addsMissingKeyInsideExistingNestedSectionWithoutTouchingSiblingsOrComments() {
        String defaultText = """
                discord:
                  enabled: true
                  # New in this version: per-player cooldown.
                  cooldown-seconds: 5
                """;
        String currentText = """
                # Admin's own note on why this is disabled.
                discord:
                  enabled: false
                """;

        ConfigUpdater.MergeOutcome outcome = ConfigUpdater.mergeAddingMissingKeys(
                yaml(defaultText), yaml(currentText), lines(defaultText), lines(currentText));

        assertEquals(1, outcome.addedKeys());
        String merged = joined(outcome.mergedLines());
        assertTrue(merged.contains("# Admin's own note on why this is disabled."), "pre-existing comment must survive");
        assertTrue(merged.contains("# New in this version: per-player cooldown."), "new key's own comment must be added");

        YamlConfiguration result = yaml(merged);
        assertFalse(result.getBoolean("discord.enabled"), "admin's existing value must not be reset to the default");
        assertEquals(5, result.getInt("discord.cooldown-seconds"));
    }

    @Test
    void addsEntirelyNewNestedSectionAsOneBlockAtEndOfFile() {
        String defaultText = """
                language: en

                # A brand new feature toggle.
                features:
                  example:
                    enabled: true
                    threshold: 3
                """;
        String currentText = """
                language: en
                """;

        ConfigUpdater.MergeOutcome outcome = ConfigUpdater.mergeAddingMissingKeys(
                yaml(defaultText), yaml(currentText), lines(defaultText), lines(currentText));

        // one "root" (the whole features: block), not 3 individual leaves
        assertEquals(1, outcome.addedKeys());
        String merged = joined(outcome.mergedLines());
        assertTrue(merged.contains("# A brand new feature toggle."));

        YamlConfiguration result = yaml(merged);
        assertTrue(result.getBoolean("features.example.enabled"));
        assertEquals(3, result.getInt("features.example.threshold"));
    }

    @Test
    void addsMultipleMissingKeysUnderSameNewParentTogetherInDefaultOrder() {
        String defaultText = """
                update-checker:
                  enabled: true
                  check-interval-minutes: 60
                """;
        String currentText = """
                language: en
                """;

        ConfigUpdater.MergeOutcome outcome = ConfigUpdater.mergeAddingMissingKeys(
                yaml(defaultText), yaml(currentText), lines(defaultText), lines(currentText));

        assertEquals(1, outcome.addedKeys()); // one root: the whole update-checker section
        YamlConfiguration result = yaml(joined(outcome.mergedLines()));
        assertTrue(result.getBoolean("update-checker.enabled"));
        assertEquals(60, result.getInt("update-checker.check-interval-minutes"));
    }

    @Test
    void fallsBackToNullWhenFileUsesTabIndentation() {
        // SnakeYAML itself rejects tab indentation, so YamlConfiguration can't be
        // asked to parse it in a unit test without a live server (its catch block
        // logs via Bukkit.getLogger()). Feed a valid, equivalent YamlConfiguration
        // but hand-crafted raw lines containing a tab, to target exactly the line
        // indexer's own tab guard instead.
        String defaultText = "discord:\n  enabled: true\n";
        YamlConfiguration current = yaml("discord:\n  enabled: false\n");
        List<String> currentLines = List.of("discord:", "\tenabled: false");

        ConfigUpdater.MergeOutcome outcome = ConfigUpdater.mergeAddingMissingKeys(
                yaml(defaultText), current, lines(defaultText), currentLines);

        assertNull(outcome, "tab-indented files aren't trusted - caller must fall back to the safe rewrite");
    }

    @Test
    void renameMovesExistingValueAndRemovesOldPath() {
        YamlConfiguration current = yaml("discord-old-link: \"https://discord.gg/my-server\"\n");

        int renamed = ConfigUpdater.applyRenames(current, Map.of("discord-old-link", "discord.link"));

        assertEquals(1, renamed);
        assertEquals("https://discord.gg/my-server", current.getString("discord.link"));
        assertFalse(current.contains("discord-old-link"));
    }

    @Test
    void renameDoesNothingWhenOldPathIsAlreadyAbsent() {
        YamlConfiguration current = yaml("discord:\n  link: \"https://discord.gg/already-migrated\"\n");

        int renamed = ConfigUpdater.applyRenames(current, Map.of("discord-old-link", "discord.link"));

        assertEquals(0, renamed);
        assertEquals("https://discord.gg/already-migrated", current.getString("discord.link"));
    }

    @Test
    void realisticUpgradeOfAnOlderStoneDiscordConfigAddsOnlyWhatIsMissing() throws Exception {
        // Uses the project's actual shipped config.yml as "the new default",
        // simulating an admin who is still on an older version that predates
        // the update-checker/media/social sections and has customized their
        // cooldown and Discord link.
        String defaultText = Files.readString(Path.of("src/main/resources/config.yml"));
        String oldAdminConfig = """
                # My own server's StoneDiscord config, don't touch!
                language: de

                discord:
                  enabled: true
                  link: "https://discord.gg/my-real-server"
                  message: "Join us: {link}"
                  cooldown-seconds: 20
                """;

        ConfigUpdater.MergeOutcome outcome = ConfigUpdater.mergeAddingMissingKeys(
                yaml(defaultText), yaml(oldAdminConfig), lines(defaultText), lines(oldAdminConfig));

        // three whole missing sections (update-checker, media, social), each
        // counted once regardless of how many keys/list items it contains
        assertEquals(3, outcome.addedKeys());

        String merged = joined(outcome.mergedLines());
        assertTrue(merged.contains("# My own server's StoneDiscord config, don't touch!"),
                "the admin's own header comment must survive untouched");
        assertTrue(merged.contains("update-checker:"), "the new section must be added");
        assertTrue(merged.contains("media:"), "the new media section must be added");
        assertTrue(merged.contains("social:"), "the new social section must be added");

        YamlConfiguration result = yaml(merged);
        // everything the admin customized must be exactly as before
        assertEquals("de", result.getString("language"));
        assertEquals("https://discord.gg/my-real-server", result.getString("discord.link"));
        assertEquals("Join us: {link}", result.getString("discord.message"));
        assertEquals(20, result.getInt("discord.cooldown-seconds"));
        // the new sections must carry the current default's values, including
        // the list of GUI items inside media/social
        assertEquals(true, result.getBoolean("update-checker.enabled"));
        assertEquals(60, result.getInt("update-checker.check-interval-minutes"));
        assertTrue(result.getBoolean("media.enabled"));
        assertFalse(result.getMapList("media.items").isEmpty());
        assertTrue(result.getBoolean("social.enabled"));
        assertFalse(result.getMapList("social.items").isEmpty());
    }

    @Test
    void renameDoesNotOverwriteNewPathIfAdminAlreadySetBoth() {
        YamlConfiguration current = yaml("old-key: \"old-value\"\nnew-key: \"admin-value\"\n");

        int renamed = ConfigUpdater.applyRenames(current, Map.of("old-key", "new-key"));

        assertEquals(0, renamed, "must not clobber a value the admin already has under the new path");
        assertEquals("admin-value", current.getString("new-key"));
    }
}
