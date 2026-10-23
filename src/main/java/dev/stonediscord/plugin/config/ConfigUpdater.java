package dev.stonediscord.plugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps an admin's config/messages file in sync with the version bundled in
 * the plugin jar: missing keys (including nested ones) are added with their
 * default value, existing values are never touched. The merge is done by
 * inserting raw lines - copied verbatim, comments included - from the
 * bundled default into the existing file's raw text, rather than parsing
 * and re-serializing the whole file, so pre-existing comments and
 * formatting survive untouched.
 * <p>
 * If a file uses a YAML shape the lightweight line parser below can't
 * reliably index (tabs, quoted/flow-style keys, duplicate keys, ...), the
 * merge falls back to the old, simpler approach of rewriting the file via
 * Bukkit's YAML writer: still correct (no values lost), just without
 * preserved formatting for that one file.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    // Requirement: carry an admin's value across a future rename/restructure
    // instead of silently losing it to the new default. Central, empty today
    // - nothing in this project has been renamed yet. Add an entry here
    // ("old.dotted.path", "new.dotted.path") *before* removing/renaming a key
    // in the bundled default resource. Deliberately a flat table instead of a
    // full versioned migration history: at this project's size one rename
    // pass per load() is enough, and it avoids maintaining a growing list of
    // numbered migration steps for what is effectively a single config file.
    private static final Map<String, String> RENAMED_KEYS = Map.of(
            // "old.path", "new.path"
    );

    private static final Pattern KEY_LINE = Pattern.compile("^( *)([A-Za-z0-9_-]+):(.*)$");

    public static UpdateResult update(JavaPlugin plugin, String resourcePath, File targetFile) throws IOException {
        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(targetFile);

        int renamedKeys = applyRenames(currentConfig);
        if (renamedKeys > 0) {
            // A rename is a rare, deliberate structural change - accept a full
            // rewrite (and the resulting loss of comments) for it, unlike the
            // additive merge below which must never do that for the common case.
            currentConfig.save(targetFile);
            plugin.getLogger().info("Renamed " + renamedKeys + " option(s) in " + resourcePath
                    + " to their new path (existing value kept).");
        }

        String defaultText = readResourceAsText(plugin, resourcePath);
        if (defaultText == null) {
            return new UpdateResult(renamedKeys > 0, 0, renamedKeys);
        }
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new StringReader(defaultText));

        List<String> currentLines = Files.readAllLines(targetFile.toPath(), StandardCharsets.UTF_8);
        List<String> defaultLines = defaultText.lines().toList();

        MergeOutcome outcome = mergeAddingMissingKeys(defaultConfig, currentConfig, defaultLines, currentLines);
        int addedKeys;
        if (outcome != null) {
            addedKeys = outcome.addedKeys();
            if (addedKeys > 0) {
                Files.write(targetFile.toPath(), outcome.mergedLines(), StandardCharsets.UTF_8);
            }
        } else {
            addedKeys = mergeValuesOnly(defaultConfig, currentConfig);
            if (addedKeys > 0) {
                currentConfig.save(targetFile);
            }
            if (addedKeys > 0) {
                plugin.getLogger().warning(resourcePath + " uses a YAML layout the comment-preserving updater "
                        + "doesn't understand; missing option(s) were still added, but existing comments in "
                        + "this file were not preserved this time.");
            }
        }

        return new UpdateResult(addedKeys > 0 || renamedKeys > 0, addedKeys, renamedKeys);
    }

    private static String readResourceAsText(JavaPlugin plugin, String resourcePath) throws IOException {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int applyRenames(YamlConfiguration current) {
        return applyRenames(current, RENAMED_KEYS);
    }

    static int applyRenames(YamlConfiguration current, Map<String, String> renames) {
        int renamed = 0;
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            String oldPath = rename.getKey();
            String newPath = rename.getValue();
            if (current.contains(oldPath, true) && !current.contains(newPath, true)) {
                current.set(newPath, current.get(oldPath));
                current.set(oldPath, null);
                renamed++;
            }
        }
        return renamed;
    }

    /**
     * Returns the merged lines plus how many top-level missing entries were
     * added, or {@code null} if the raw text couldn't be indexed reliably
     * (caller must then fall back to {@link #mergeValuesOnly}).
     */
    static MergeOutcome mergeAddingMissingKeys(YamlConfiguration defaults, YamlConfiguration current,
                                                List<String> defaultLines, List<String> currentLines) {
        Map<String, LineRange> defaultIndex = indexByPath(defaultLines);
        Map<String, LineRange> currentIndex = indexByPath(currentLines);
        if (defaultIndex == null || currentIndex == null) {
            return null;
        }
        // Our lightweight parser must have found every path Bukkit's real YAML
        // parser knows about, or its line ranges/insertion points can't be
        // trusted - fall back instead of risking a misplaced insertion.
        if (!defaultIndex.keySet().containsAll(defaults.getKeys(true))
                || !currentIndex.keySet().containsAll(current.getKeys(true))) {
            return null;
        }

        List<String> missingRoots = new ArrayList<>();
        for (String path : defaultIndex.keySet()) {
            if (current.contains(path, true)) {
                continue;
            }
            String parent = parentPath(path);
            if (parent != null && !current.contains(parent, true)) {
                continue; // will be fully covered when the shallower missing parent is inserted
            }
            missingRoots.add(path);
        }
        if (missingRoots.isEmpty()) {
            return new MergeOutcome(currentLines, 0);
        }

        // Group by insertion point so entries sharing a parent land together,
        // in the order they appear in the default file.
        TreeMap<Integer, List<String>> insertionsByIndex = new TreeMap<>();
        for (String path : missingRoots) {
            String parent = parentPath(path);
            int insertAt = parent == null ? currentLines.size() : currentIndex.get(parent).end();
            List<String> snippet = extractTrimmed(defaultLines, defaultIndex.get(path));
            insertionsByIndex.computeIfAbsent(insertAt, k -> new ArrayList<>()).addAll(snippet);
        }

        List<String> result = new ArrayList<>(currentLines);
        for (int target : insertionsByIndex.descendingKeySet()) {
            List<String> block = insertionsByIndex.get(target);
            List<String> toInsert = new ArrayList<>(block.size() + 1);
            if (target > 0 && !currentLines.get(target - 1).isBlank()) {
                toInsert.add("");
            }
            toInsert.addAll(block);
            result.addAll(target, toInsert);
        }

        return new MergeOutcome(result, missingRoots.size());
    }

    private static String parentPath(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? null : path.substring(0, dot);
    }

    private static List<String> extractTrimmed(List<String> lines, LineRange range) {
        int end = range.end();
        while (end > range.start() && lines.get(end - 1).isBlank()) {
            end--;
        }
        return new ArrayList<>(lines.subList(range.start(), end));
    }

    /**
     * Maps every dotted key path (matching what {@code ConfigurationSection
     * #getKeys(true)} reports) to the line range covering its directly
     * attached leading comment block (consecutive '#' lines with no blank
     * line in between), its own "key:" line, and everything more deeply
     * indented below it (nested keys, list items, block scalars).
     * <p>
     * Only understands plain space-indented "key:" / "# comment" / blank
     * lines - the shape this project's own config.yml/messages.yml already
     * use. Returns {@code null} the moment a line doesn't fit that shape, a
     * tab is found, or a duplicate key turns up at the same level.
     */
    private static Map<String, LineRange> indexByPath(List<String> lines) {
        Map<String, LineRange> index = new LinkedHashMap<>();
        List<StackFrame> stack = new ArrayList<>();
        int pendingCommentStart = -1;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String trimmed = raw.strip();

            if (trimmed.isEmpty()) {
                pendingCommentStart = -1;
                continue;
            }
            if (trimmed.startsWith("#")) {
                if (pendingCommentStart < 0) {
                    pendingCommentStart = i;
                }
                continue;
            }
            if (raw.indexOf('\t') >= 0) {
                return null;
            }

            Matcher matcher = KEY_LINE.matcher(raw);
            if (!matcher.matches()) {
                // List item ("- foo") or block-scalar continuation line - it
                // belongs to whichever key is currently open on the stack;
                // nothing new to index for this line itself.
                pendingCommentStart = -1;
                continue;
            }

            int indent = matcher.group(1).length();
            String key = matcher.group(2);
            int entryStart = pendingCommentStart >= 0 ? pendingCommentStart : i;
            pendingCommentStart = -1;

            while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= indent) {
                StackFrame closed = stack.remove(stack.size() - 1);
                LineRange previous = index.get(closed.path());
                index.put(closed.path(), new LineRange(previous.start(), entryStart));
            }

            String path = stack.isEmpty() ? key : stack.get(stack.size() - 1).path() + "." + key;
            if (index.containsKey(path)) {
                return null; // duplicate key at this level - not a shape we trust ourselves to merge
            }
            index.put(path, new LineRange(entryStart, lines.size()));
            stack.add(new StackFrame(indent, path));
        }

        while (!stack.isEmpty()) {
            StackFrame closed = stack.remove(stack.size() - 1);
            LineRange previous = index.get(closed.path());
            index.put(closed.path(), new LineRange(previous.start(), lines.size()));
        }

        return index;
    }

    /**
     * Fallback merge used only when {@link #mergeAddingMissingKeys} can't
     * trust the raw text: same additive semantics (missing keys/sections
     * added with their default, existing values never touched), but the
     * caller must {@code save()} the result, which rewrites the whole file
     * and does not preserve comments.
     */
    private static int mergeValuesOnly(ConfigurationSection defaults, ConfigurationSection current) {
        int added = 0;
        for (String key : defaults.getKeys(false)) {
            Object defaultValue = defaults.get(key);

            if (!current.contains(key, true)) {
                current.set(key, defaultValue);
                added++;
                continue;
            }

            if (defaultValue instanceof ConfigurationSection defaultSection && current.isConfigurationSection(key)) {
                added += mergeValuesOnly(defaultSection, current.getConfigurationSection(key));
            }
        }
        return added;
    }

    record MergeOutcome(List<String> mergedLines, int addedKeys) {
    }

    private record LineRange(int start, int end) {
    }

    private record StackFrame(int indent, String path) {
    }

    public record UpdateResult(boolean changed, int addedKeys, int renamedKeys) {
    }
}
