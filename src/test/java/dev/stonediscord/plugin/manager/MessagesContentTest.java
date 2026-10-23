package dev.stonediscord.plugin.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard: media.link-sent/social.link-sent are sent via
 * MessageManager#sendChat, which already prepends the "prefix" key
 * automatically. Baking the prefix into the message text itself (as was
 * briefly done when these keys were first added) makes it show up twice
 * in chat.
 */
class MessagesContentTest {

    private static void assertNoHardcodedPrefix(String languageFile) throws Exception {
        String text = Files.readString(Path.of("src/main/resources/languages/" + languageFile + "/messages.yml"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(text));

        for (String path : new String[]{"media.link-sent", "social.link-sent"}) {
            String raw = yaml.getString(path);
            assertFalse(raw.contains("[&7Stone"), path + " in " + languageFile
                    + "/messages.yml must not bake in the chat prefix - sendChat() already "
                    + "prepends it, baking it in too would show it twice");
        }
    }

    @Test
    void englishLinkSentMessagesDoNotDuplicateThePrefix() throws Exception {
        assertNoHardcodedPrefix("en");
    }

    @Test
    void germanLinkSentMessagesDoNotDuplicateThePrefix() throws Exception {
        assertNoHardcodedPrefix("de");
    }

    private static void assertLinkSentMessagesRenderCleanly(String languageFile) throws Exception {
        String text = Files.readString(Path.of("src/main/resources/languages/" + languageFile + "/messages.yml"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(text));
        MessageManager mm = new MessageManager(null);

        for (String path : new String[]{"media.link-sent", "social.link-sent"}) {
            String raw = yaml.getString(path);
            // Must not throw (nested <hover>/<click>/hex-color/<u> tags all
            // closed in the right order) and the link itself must actually
            // show up in the rendered text.
            Component rendered = mm.format(raw, Map.of("link", "https://example.com/test"));
            String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
            assertTrue(plain.contains("https://example.com/test"), path + " in " + languageFile + "/messages.yml");
        }
    }

    @Test
    void englishLinkSentMessagesRenderCleanly() throws Exception {
        assertLinkSentMessagesRenderCleanly("en");
    }

    @Test
    void germanLinkSentMessagesRenderCleanly() throws Exception {
        assertLinkSentMessagesRenderCleanly("de");
    }
}
